package com.lastcoffee.top.oppo_call_compose.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import com.lastcoffee.top.oppo_call_compose.model.CallKind
import com.lastcoffee.top.oppo_call_compose.model.CallAudioMode
import com.lastcoffee.top.oppo_call_compose.model.CallButtonId
import com.lastcoffee.top.oppo_call_compose.model.CallButtonRule
import com.lastcoffee.top.oppo_call_compose.model.CallFlowConfiguration
import com.lastcoffee.top.oppo_call_compose.model.CallMediaEntry
import com.lastcoffee.top.oppo_call_compose.model.CallSessionSetup
import com.lastcoffee.top.oppo_call_compose.model.CallStage
import com.lastcoffee.top.oppo_call_compose.model.CallStatusRule
import com.lastcoffee.top.oppo_call_compose.model.CallVisualStyle
import com.lastcoffee.top.oppo_call_compose.model.defaultCallStatuses
import com.lastcoffee.top.oppo_call_compose.model.CallRecord
import com.lastcoffee.top.oppo_call_compose.model.ManualCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class ManualCallStore(context: Context) {
    private val appContext = context.applicationContext
    private val database = Room.databaseBuilder(appContext, ManualCallDatabase::class.java, "manual_calls.db")
        .addMigrations(ManualCallMigration1To2, ManualCallMigration2To3, ManualCallMigration3To4)
        .build()
    private val dao = database.manualCallDao()
    private val importMutex = Mutex()
    private val configurationMutex = Mutex()

    val calls: Flow<List<ManualCall>> = dao.observeAll().map { rows -> rows.map { it.toManualCall() } }
    val hiddenDemoIndexes: Flow<List<Int>> = dao.observeHiddenDemoIndexes()
    val callConfiguration: Flow<CallFlowConfiguration> = dao.observeCallConfiguration().map { it?.content?.let { raw -> decodeConfiguration(raw) } ?: CallFlowConfiguration() }
    val mediaEntries: Flow<List<CallMediaEntry>> = dao.observeMediaEntries().map { rows -> rows.map { it.toCallMediaEntry() } }
    val numberPools: Flow<List<CallNumberPoolEntity>> = dao.observeNumberPools()

    suspend fun configuration(): CallFlowConfiguration = configurationMutex.withLock {
        val saved = dao.getCallConfiguration()
        if (saved != null) {
            val config = decodeConfiguration(saved.content)
            val missingUnreachable = config.statuses.none { it.id == "unreachable" }
            if (missingUnreachable) {
                config.copy(statuses = config.statuses + defaultCallStatuses().first { it.id == "unreachable" })
                    .also { dao.saveCallConfiguration(CallRuleConfigEntity(content = encodeConfiguration(it))) }
            } else config
        } else {
            val defaults = CallFlowConfiguration()
            dao.saveCallConfiguration(CallRuleConfigEntity(content = encodeConfiguration(defaults)))
            defaults
        }
    }

    suspend fun saveConfiguration(configuration: CallFlowConfiguration) = configurationMutex.withLock {
        val validIds = configuration.statuses.map { it.id }.toSet()
        val normalized = configuration.copy(
            initialStatusId = configuration.initialStatusId.takeIf { it in validIds } ?: configuration.statuses.firstOrNull()?.id ?: "dialing",
            statuses = configuration.statuses.map { status ->
                status.copy(
                    durationSeconds = status.durationSeconds.coerceIn(0, 86_400),
                    timeoutTargetId = status.timeoutTargetId?.takeIf { it in validIds },
                    buttons = status.buttons.map { button -> button.copy(targetStatusId = button.targetStatusId?.takeIf { it in validIds }) },
                )
            },
        )
        dao.saveCallConfiguration(CallRuleConfigEntity(content = encodeConfiguration(normalized)))
    }

    suspend fun prepareCall(number: String, preferredStatusId: String? = null, forceDialing: Boolean = false): CallSessionSetup {
        val config = configuration()
        val normalizedNumber = number.filter { it.isDigit() || it == '+' }
        val existing = dao.getNumberPool(normalizedNumber)
        val mode = existing?.let { CallAudioMode.fromStored(it.mode) } ?: config.selectedAudioMode.also {
            dao.saveNumberPool(CallNumberPoolEntity(number = normalizedNumber, mode = it.name))
        }
        val entryStatusId = if (forceDialing) {
            config.statuses.firstOrNull { it.stage == CallStage.DIALING }?.id ?: config.initialStatusId
        } else preferredStatusId?.takeIf { id -> config.statuses.any { it.id == id } } ?: config.initialStatusId
        return CallSessionSetup(mode, entryStatusId, config)
    }

    suspend fun addMedia(entry: CallMediaEntry) {
        dao.saveMediaEntry(entry.toEntity())
    }

    suspend fun deleteMedia(id: String): CallMediaEntry? {
        val media = dao.getMediaEntry(id)?.toCallMediaEntry() ?: return null
        dao.deleteMediaEntry(id)
        return media
    }

    suspend fun updateNumberMode(number: String, mode: CallAudioMode) {
        dao.saveNumberPool(CallNumberPoolEntity(number = number.filter { it.isDigit() || it == '+' }, mode = mode.name))
    }

    suspend fun addNumberToPool(number: String) {
        val normalized = normalizePhoneNumber(number)
        if (normalized.length !in 3..20) return
        if (dao.getAllNumberPools().none { normalizePhoneNumber(it.number) == normalized }) {
            dao.saveNumberPool(CallNumberPoolEntity(normalized, configuration().selectedAudioMode.name))
        }
    }

    suspend fun ensureConfiguredNumbers(numbers: Set<String>) {
        val existing = dao.getAllNumberPools().map { normalizePhoneNumber(it.number) }.toMutableSet()
        val defaultMode = configuration().selectedAudioMode.name
        numbers.map(::normalizePhoneNumber).filter { it.length in 3..20 }.distinct().forEach { number ->
            if (existing.add(number)) dao.saveNumberPool(CallNumberPoolEntity(number, defaultMode))
        }
    }

    suspend fun add(call: ManualCall) {
        importLegacyRecords()
        dao.insert(call.toEntity())
    }

    suspend fun delete(record: CallRecord) {
        record.storedId?.let { dao.deleteById(it) }
        record.demoIndex?.let { dao.hideDemo(HiddenDemoCallEntity(it)) }
    }

    suspend fun clearAll(demoCount: Int) {
        database.withTransaction {
            dao.deleteAllCalls()
            dao.hideDemo((0 until demoCount).map(::HiddenDemoCallEntity))
        }
    }

    suspend fun importLegacyRecords() = withContext(Dispatchers.IO) {
        importMutex.withLock {
            val preferences = appContext.getSharedPreferences("manual_call_records", Context.MODE_PRIVATE)
            val raw = preferences.getString("records", null) ?: return@withLock
            val legacy = runCatching { JSONArray(raw) }.getOrNull() ?: return@withLock
            val rows = buildList {
                for (index in 0 until legacy.length()) {
                    val item = legacy.optJSONObject(index) ?: continue
                    val number = item.optString("number")
                    val kind = CallKind.entries.firstOrNull { it.name == item.optString("kind") } ?: continue
                    val timeMillis = item.optLong("timeMillis", -1L)
                    val durationSeconds = item.optInt("durationSeconds", -1)
                    if (number.isBlank() || timeMillis < 0 || durationSeconds < 0) continue
                    val id = item.optString("id").takeIf { it.isNotBlank() } ?: "legacy-$index"
                    add(ManualCall(id, number, kind, durationSeconds, timeMillis).toEntity())
                }
            }
            dao.insertLegacy(rows)
            check(preferences.edit().remove("records").commit()) { "Could not finish legacy call record migration" }
        }
    }
}

@Entity(tableName = "manual_calls")
data class ManualCallEntity(
    @PrimaryKey val id: String,
    val number: String,
    val kind: String,
    val durationSeconds: Int,
    val timeMillis: Long,
    val wasConnected: Boolean,
)

@Entity(tableName = "hidden_demo_calls")
data class HiddenDemoCallEntity(@PrimaryKey val demoIndex: Int)

@Entity(tableName = "call_rule_config")
data class CallRuleConfigEntity(@PrimaryKey val id: Int = 1, val content: String)

@Entity(tableName = "call_number_pools")
data class CallNumberPoolEntity(@PrimaryKey val number: String, val mode: String)

@Entity(tableName = "call_media")
data class CallMediaEntity(
    @PrimaryKey val id: String, val mode: String, val title: String, val filePath: String, val mimeType: String, val iconPath: String?,
)

private fun ManualCall.toEntity() = ManualCallEntity(id, number, kind.name, durationSeconds, timeMillis, wasConnected)

private fun ManualCallEntity.toManualCall() = ManualCall(id, number, CallKind.valueOf(kind), durationSeconds, timeMillis, wasConnected)
private fun CallMediaEntry.toEntity() = CallMediaEntity(id, mode.name, title, filePath, mimeType, iconPath)
private fun CallMediaEntity.toCallMediaEntry() = CallMediaEntry(id, CallAudioMode.fromStored(mode), title, filePath, mimeType, iconPath)

private fun encodeConfiguration(config: CallFlowConfiguration): String = JSONObject().apply {
    put("initialStatusId", config.initialStatusId)
    put("selectedAudioMode", config.selectedAudioMode.name)
    put("statuses", JSONArray().apply {
        config.statuses.forEach { status -> put(JSONObject().apply {
            put("id", status.id); put("name", status.name); put("stage", status.stage.name)
            put("durationSeconds", status.durationSeconds); put("timeoutTargetId", status.timeoutTargetId ?: JSONObject.NULL)
            put("outcome", status.outcome ?: JSONObject.NULL)
            put("mediaEntryId", status.mediaEntryId ?: JSONObject.NULL)
            put("soundEntryId", status.soundEntryId ?: JSONObject.NULL)
            put("playSoundOnce", status.playSoundOnce)
            put("soundMuted", status.soundMuted)
            put("callerNameStyle", status.callerNameStyle.toJson())
            put("callerNumberStyle", status.callerNumberStyle.toJson())
            put("locationTextStyle", status.locationTextStyle.toJson())
            put("statusTextStyle", status.statusTextStyle.toJson())
            put("buttons", JSONArray().apply { status.buttons.forEach { button -> put(JSONObject().apply {
                put("id", button.id.name); put("visible", button.visible); put("enabled", button.enabled)
                put("targetStatusId", button.targetStatusId ?: JSONObject.NULL)
                put("label", button.label ?: JSONObject.NULL)
                put("visualStyle", button.visualStyle.toJson())
            }) } })
        }) }
    })
}.toString()

private fun decodeConfiguration(raw: String): CallFlowConfiguration = runCatching {
    val root = JSONObject(raw)
    val statusesJson = root.optJSONArray("statuses") ?: JSONArray()
    val statuses = buildList {
        for (i in 0 until statusesJson.length()) {
            val item = statusesJson.optJSONObject(i) ?: continue
            val stage = CallStage.entries.firstOrNull { it.name == item.optString("stage") } ?: CallStage.WAITING
            val buttonsJson = item.optJSONArray("buttons") ?: JSONArray()
            val buttons = buildList {
                for (j in 0 until buttonsJson.length()) {
                    val button = buttonsJson.optJSONObject(j) ?: continue
                    val storedId = button.optString("id")
                    val id = when (storedId) {
                        "SECONDARY_ONE" -> CallButtonId.HOLD
                        "SECONDARY_TWO" -> if (stage == CallStage.DIALING || stage == CallStage.WAITING) CallButtonId.VIDEO_CALL else CallButtonId.RECORD
                        "SECONDARY_THREE" -> CallButtonId.ADD_CALL
                        else -> CallButtonId.entries.firstOrNull { it.name == storedId } ?: continue
                    }
                    add(CallButtonRule(id, button.optBoolean("visible", true), button.optBoolean("enabled", true), button.optString("targetStatusId").takeIf { it.isNotBlank() && it != "null" }, button.optString("label").takeIf { it.isNotBlank() && it != "null" }, button.optJSONObject("visualStyle")?.toVisualStyle() ?: CallVisualStyle()))
                }
            }.ifEmpty { com.lastcoffee.top.oppo_call_compose.model.defaultButtons(stage) }
            add(CallStatusRule(
                id = item.optString("id", "status-$i"), name = item.optString("name", "状态 ${i + 1}"), stage = stage,
                durationSeconds = item.optInt("durationSeconds", 0), timeoutTargetId = item.optString("timeoutTargetId").takeIf { it.isNotBlank() && it != "null" },
                buttons = buttons, outcome = item.optString("outcome").takeIf { it.isNotBlank() && it != "null" },
                mediaEntryId = item.optString("mediaEntryId").takeIf { it.isNotBlank() && it != "null" },
                soundEntryId = item.optString("soundEntryId").takeIf { it.isNotBlank() && it != "null" },
                playSoundOnce = item.optBoolean("playSoundOnce", false),
                soundMuted = item.optBoolean("soundMuted", false),
                callerNameStyle = item.optJSONObject("callerNameStyle")?.toVisualStyle() ?: CallVisualStyle(),
                callerNumberStyle = item.optJSONObject("callerNumberStyle")?.toVisualStyle() ?: CallVisualStyle(),
                locationTextStyle = item.optJSONObject("locationTextStyle")?.toVisualStyle() ?: CallVisualStyle(),
                statusTextStyle = item.optJSONObject("statusTextStyle")?.toVisualStyle() ?: CallVisualStyle(),
            ))
        }
    }.ifEmpty { defaultCallStatuses() }
    val ids = statuses.map { it.id }.toSet()
    CallFlowConfiguration(
        initialStatusId = root.optString("initialStatusId", "dialing").takeIf { it in ids } ?: statuses.first().id,
        selectedAudioMode = CallAudioMode.fromStored(root.optString("selectedAudioMode")), statuses = statuses,
    )
}.getOrElse { CallFlowConfiguration() }

private fun CallVisualStyle.toJson() = JSONObject().apply {
    put("offsetXdp", offsetXdp); put("offsetYdp", offsetYdp)
    put("fontSizeSp", fontSizeSp ?: JSONObject.NULL)
    put("textColorArgb", textColorArgb ?: JSONObject.NULL)
    put("backgroundColorArgb", backgroundColorArgb ?: JSONObject.NULL)
    put("visible", visible)
}

private fun JSONObject.toVisualStyle() = CallVisualStyle(
    offsetXdp = optInt("offsetXdp", 0),
    offsetYdp = optInt("offsetYdp", 0),
    fontSizeSp = if (isNull("fontSizeSp")) null else optDouble("fontSizeSp").toFloat(),
    textColorArgb = if (isNull("textColorArgb")) null else optLong("textColorArgb"),
    backgroundColorArgb = if (isNull("backgroundColorArgb")) null else optLong("backgroundColorArgb"),
    visible = optBoolean("visible", true),
)

private val ManualCallMigration1To2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE manual_calls ADD COLUMN wasConnected INTEGER NOT NULL DEFAULT 1")
    }
}

private val ManualCallMigration2To3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS hidden_demo_calls (demoIndex INTEGER NOT NULL, PRIMARY KEY(demoIndex))")
    }
}

private val ManualCallMigration3To4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS call_rule_config (id INTEGER NOT NULL, content TEXT NOT NULL, PRIMARY KEY(id))")
        db.execSQL("CREATE TABLE IF NOT EXISTS call_number_pools (number TEXT NOT NULL, mode TEXT NOT NULL, PRIMARY KEY(number))")
        db.execSQL("CREATE TABLE IF NOT EXISTS call_media (id TEXT NOT NULL, mode TEXT NOT NULL, title TEXT NOT NULL, filePath TEXT NOT NULL, mimeType TEXT NOT NULL, iconPath TEXT, PRIMARY KEY(id))")
    }
}

@Dao
interface ManualCallDao {
    @Query("SELECT * FROM manual_calls ORDER BY timeMillis DESC, id DESC")
    fun observeAll(): Flow<List<ManualCallEntity>>

    @Insert
    suspend fun insert(call: ManualCallEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLegacy(calls: List<ManualCallEntity>)

    @Query("DELETE FROM manual_calls WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM manual_calls")
    suspend fun deleteAllCalls()

    @Query("SELECT demoIndex FROM hidden_demo_calls ORDER BY demoIndex")
    fun observeHiddenDemoIndexes(): Flow<List<Int>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun hideDemo(call: HiddenDemoCallEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun hideDemo(calls: List<HiddenDemoCallEntity>)

    @Query("SELECT * FROM call_rule_config WHERE id = 1")
    fun observeCallConfiguration(): Flow<CallRuleConfigEntity?>

    @Query("SELECT * FROM call_rule_config WHERE id = 1")
    suspend fun getCallConfiguration(): CallRuleConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCallConfiguration(config: CallRuleConfigEntity)

    @Query("SELECT * FROM call_number_pools ORDER BY number")
    fun observeNumberPools(): Flow<List<CallNumberPoolEntity>>

    @Query("SELECT * FROM call_number_pools")
    suspend fun getAllNumberPools(): List<CallNumberPoolEntity>

    @Query("SELECT * FROM call_number_pools WHERE number = :number LIMIT 1")
    suspend fun getNumberPool(number: String): CallNumberPoolEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveNumberPool(entry: CallNumberPoolEntity)

    @Query("SELECT * FROM call_media ORDER BY title COLLATE NOCASE")
    fun observeMediaEntries(): Flow<List<CallMediaEntity>>

    @Query("SELECT * FROM call_media WHERE id = :id LIMIT 1")
    suspend fun getMediaEntry(id: String): CallMediaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveMediaEntry(entry: CallMediaEntity)

    @Query("DELETE FROM call_media WHERE id = :id")
    suspend fun deleteMediaEntry(id: String)
}

@Database(entities = [ManualCallEntity::class, HiddenDemoCallEntity::class, CallRuleConfigEntity::class, CallNumberPoolEntity::class, CallMediaEntity::class], version = 4, exportSchema = true)
abstract class ManualCallDatabase : RoomDatabase() {
    abstract fun manualCallDao(): ManualCallDao
}
