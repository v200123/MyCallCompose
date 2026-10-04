package com.lastcoffee.top.oppo_call_compose

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

data class ManualCall(
    val id: String = UUID.randomUUID().toString(),
    val number: String,
    val kind: CallKind,
    val durationSeconds: Int,
    val timeMillis: Long,
) {
    fun toCallRecord(): CallRecord {
        val time = Instant.ofEpochMilli(timeMillis).atZone(ZoneId.systemDefault())
        val type = kind.displayLabel()
        val detail = if (kind == CallKind.MISSED || kind == CallKind.BLOCKED) "未接通" else formatCallDuration(durationSeconds)
        return CallRecord(
            number = number,
            description = "$type  $detail",
            date = time.format(DateTimeFormatter.ofPattern("yyyy/M/d")),
            kind = kind,
            detailTime = time.format(DateTimeFormatter.ofPattern("yyyy/M/d HH:mm")),
            durationDetail = "$type  $detail",
        )
    }
}

fun CallKind.displayLabel(): String = when (this) {
    CallKind.INCOMING -> "呼入"
    CallKind.OUTGOING -> "呼出"
    CallKind.MISSED -> "未接来电"
    CallKind.BLOCKED -> "拒接"
}

fun formatCallDuration(seconds: Int): String = when {
    seconds >= 3_600 -> "${seconds / 3_600}小时${(seconds % 3_600) / 60}分${seconds % 60}秒"
    seconds >= 60 -> "${seconds / 60}分${seconds % 60}秒"
    else -> "${seconds}秒"
}

class ManualCallStore(context: Context) {
    private val appContext = context.applicationContext
    private val database = Room.databaseBuilder(appContext, ManualCallDatabase::class.java, "manual_calls.db").build()
    private val dao = database.manualCallDao()
    private val importMutex = Mutex()

    val calls: Flow<List<ManualCall>> = dao.observeAll().map { rows -> rows.map { it.toManualCall() } }

    suspend fun add(call: ManualCall) {
        importLegacyRecords()
        dao.insert(call.toEntity())
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
)

private fun ManualCall.toEntity() = ManualCallEntity(id, number, kind.name, durationSeconds, timeMillis)

private fun ManualCallEntity.toManualCall() = ManualCall(id, number, CallKind.valueOf(kind), durationSeconds, timeMillis)

@Dao
interface ManualCallDao {
    @Query("SELECT * FROM manual_calls ORDER BY timeMillis DESC, id DESC")
    fun observeAll(): Flow<List<ManualCallEntity>>

    @Insert
    suspend fun insert(call: ManualCallEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLegacy(calls: List<ManualCallEntity>)
}

@Database(entities = [ManualCallEntity::class], version = 1, exportSchema = true)
abstract class ManualCallDatabase : RoomDatabase() {
    abstract fun manualCallDao(): ManualCallDao
}
