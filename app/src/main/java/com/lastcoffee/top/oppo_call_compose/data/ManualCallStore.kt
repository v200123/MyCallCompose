package com.lastcoffee.top.oppo_call_compose.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.File
import java.util.UUID
import com.lastcoffee.top.oppo_call_compose.R
import com.lastcoffee.top.oppo_call_compose.model.withDefaultStatusAudio
import androidx.room.Dao
import androidx.room.ColumnInfo
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
import com.lastcoffee.top.oppo_call_compose.model.CallScreenVariant
import com.lastcoffee.top.oppo_call_compose.model.CallStage
import com.lastcoffee.top.oppo_call_compose.model.DEFAULT_WAITING_SECONDS
import com.lastcoffee.top.oppo_call_compose.model.CallStatusRule
import com.lastcoffee.top.oppo_call_compose.model.CallVisualStyle
import com.lastcoffee.top.oppo_call_compose.model.defaultCallStatuses
import com.lastcoffee.top.oppo_call_compose.model.withRequiredCallStatuses
import com.lastcoffee.top.oppo_call_compose.model.withProgrammedCallFlow
import com.lastcoffee.top.oppo_call_compose.model.mergeLegacyCallStatuses
import com.lastcoffee.top.oppo_call_compose.model.withVisibleCallButtons
import com.lastcoffee.top.oppo_call_compose.model.CallRecord
import com.lastcoffee.top.oppo_call_compose.model.ManualCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class ManualCallStore internal constructor(context: Context, private val database: ManualCallDatabase) {
    constructor(context: Context) : this(context, openManualCallDatabase(context.applicationContext))

    private val appContext = context.applicationContext
    private val bundledMedia by lazy {
        val directory = File(appContext.filesDir, "bundled_status_audio").apply { mkdirs() }
        listOf(
            Triple("waiting", "等待接听", R.raw.audio_call),
            Triple("unreachable", "无法接通", R.raw.audio_busy),
            Triple("busy", "正在通话中", R.raw.default_busy),
            Triple("no_answer", "暂时无人接听", R.raw.default_no_answer),
            Triple("powered_off", "关机", R.raw.audio_power_off),
            Triple("voicemail", "语音留言", R.raw.default_voicemail),
            Triple("legacy_no_answer", "旧版无人接听", R.raw.audio_no_response),
            Triple("legacy_busy", "旧版用户正忙", R.raw.audio_user_busy),
            Triple("legacy_empty_number", "旧版空号", R.raw.audio_empty_number),
            Triple("legacy_powered_off", "旧版关机", R.raw.legacy_power_off),
            Triple("legacy_hangup", "旧版挂断音", R.raw.telephone_hang_up_call),
        ).map { (key, title, resource) ->
            val extension = if (key in setOf("voicemail", "unreachable", "legacy_busy")) "m4a" else "mp3"
            val file = File(directory, "$key.$extension")
            if (!file.isFile || file.length() == 0L) {
                appContext.resources.openRawResource(resource).use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
            }
            val mode = if (key == "waiting") CallAudioMode.DEFAULT_TONE else CallAudioMode.STATUS_SOUND
            CallMediaEntry("builtin_$key", mode, title, file.absolutePath,
                if (extension == "m4a") "audio/mp4" else "audio/mpeg")
        }
    }
    private fun includeBundledMedia(entries: List<CallMediaEntry>) =
        entries.filterNot { entry -> bundledMedia.any { it.id == entry.id } } + bundledMedia
    private val dao = database.manualCallDao()
    private val importMutex = Mutex()
    private val configurationMutex = Mutex()
    private val recordingsMutex = Mutex()
    private val numberBindingsMutex = Mutex()
    private val recordingsDir = File(appContext.filesDir, "call_recordings")

    val calls: Flow<List<ManualCall>> = dao.observeAll().map { rows -> rows.map { it.toManualCall() } }
    val hiddenDemoIndexes: Flow<List<Int>> = dao.observeHiddenDemoIndexes()
    val callConfiguration: Flow<CallFlowConfiguration> = dao.observeCallConfiguration().map { it?.content?.let { raw -> decodeConfiguration(raw) } ?: CallFlowConfiguration() }
    val mediaEntries: Flow<List<CallMediaEntry>> = dao.observeMediaEntries().map { rows -> includeBundledMedia(rows.map { it.toCallMediaEntry() }) }
    val numberPools: Flow<List<CallNumberPoolEntity>> = dao.observeNumberPools()

    suspend fun exportTransferState(): CallTransferState = database.withTransaction {
        CallTransferState(
            dao.getCallConfiguration()?.content?.let { encodeConfiguration(decodeConfiguration(it)) }
                ?: encodeConfiguration(CallFlowConfiguration().withProgrammedCallFlow()),
            dao.getAllMediaEntries().map { it.toCallMediaEntry() }.filterNot { entry -> bundledMedia.any { it.id == entry.id } },
        )
    }

    suspend fun replaceTransferState(state: CallTransferState) = configurationMutex.withLock {
        val root = JSONObject(state.configurationJson)
        require((root.optJSONArray("statuses")?.length() ?: 0) > 0) { "通话规则为空" }
        val importedIds = state.mediaEntries.map { it.id }.toSet()
        require(importedIds.size == state.mediaEntries.size) { "媒体编号重复" }
        val mediaIds = importedIds + bundledMedia.map { it.id }
        require(state.mediaEntries.all { it.id.isNotBlank() && it.title.isNotBlank() && it.mode.acceptsMimeType(it.mimeType) }) { "媒体信息无效" }
        val previous = dao.getCallConfiguration()?.content?.let(::decodeConfiguration) ?: CallFlowConfiguration()
        val configuration = decodeConfiguration(state.configurationJson).withRequiredCallStatuses(previous).withProgrammedCallFlow().let { config ->
            fun keepMedia(statuses: List<CallStatusRule>) = statuses.map { status -> status.copy(
                mediaEntryId = status.mediaEntryId?.takeIf { it in mediaIds },
                soundEntryId = status.soundEntryId?.takeIf { it in mediaIds },
            ) }
            config.copy(statuses = keepMedia(config.statuses))
        }
        database.withTransaction {
            dao.deleteAllMediaEntries()
            state.mediaEntries.forEach { dao.saveMediaEntry(it.toEntity()) }
            dao.saveCallConfiguration(CallRuleConfigEntity(content = encodeConfiguration(configuration)))
        }
    }

    suspend fun configuration(): CallFlowConfiguration = configurationMutex.withLock {
        val saved = dao.getCallConfiguration()
        val config = saved?.content?.let(::decodeConfiguration) ?: CallFlowConfiguration().withProgrammedCallFlow()
        val encoded = encodeConfiguration(config)
        if (saved?.content != encoded) dao.saveCallConfiguration(CallRuleConfigEntity(content = encoded))
        config
    }

    suspend fun setSelectedAudioMode(mode: CallAudioMode) = configurationMutex.withLock {
        val current = dao.getCallConfiguration()?.content?.let(::decodeConfiguration) ?: CallFlowConfiguration()
        dao.saveCallConfiguration(CallRuleConfigEntity(content = encodeConfiguration(current.copy(selectedAudioMode = mode).withProgrammedCallFlow())))
    }

    suspend fun saveCallStatuses(statuses: List<CallStatusRule>) = configurationMutex.withLock {
        val previous = dao.getCallConfiguration()?.content?.let(::decodeConfiguration) ?: CallFlowConfiguration()
        val configuration = normalizeConfiguration(previous.copy(statuses = statuses), previous)
        dao.saveCallConfiguration(CallRuleConfigEntity(content = encodeConfiguration(configuration)))
    }

    suspend fun prepareCall(
        number: String,
        variant: CallScreenVariant = CallScreenVariant.forNumber(number),
        extras: PhoneExtras = PhoneExtras(),
    ): CallSessionSetup = numberBindingsMutex.withLock {
        val config = configuration()
        val normalizedNumber = normalizePhoneNumber(number)
        database.withTransaction {
            val existing = dao.getNumberPool(normalizedNumber)
                ?: dao.getAllNumberPools().firstOrNull { normalizePhoneNumber(it.number) == normalizedNumber }
            val entries = includeBundledMedia(dao.getAllMediaEntries().map { it.toCallMediaEntry() })
                .filter { it.mode.acceptsMimeType(it.mimeType) && File(it.filePath).isFile }
            val explicitRingback = entries.firstOrNull {
                it.id == extras.ringbackBindings[normalizedNumber] && it.mode == CallAudioMode.RINGBACK
            }
            val mode = if (explicitRingback != null) CallAudioMode.RINGBACK
                else existing?.let { CallAudioMode.fromStored(it.mode) }?.takeIf { it.isCallMode }
                    ?: config.selectedAudioMode
            val candidates = entries.filter { it.mode == mode }
            val selected = explicitRingback
                ?: candidates.firstOrNull { it.id == existing?.waitingMediaId }
                ?: if (mode == CallAudioMode.RINGBACK) {
                    candidates.firstOrNull { it.id == extras.defaultRingbackId } ?: candidates.randomOrNull()
                } else if (mode == CallAudioMode.DEFAULT_TONE) candidates.firstOrNull() else candidates.randomOrNull()
            val binding = CallNumberPoolEntity(normalizedNumber, mode.name,
                waitingMediaId = selected?.id.takeUnless { mode == CallAudioMode.DEFAULT_TONE })
            if (existing?.number != null && existing.number != normalizedNumber) dao.deleteNumberPool(existing.number)
            dao.saveNumberPool(binding)
            val mediaId = if (mode == CallAudioMode.RINGBACK && extras.blocksRingback(number)) null else selected?.id
            val fallback = entries.firstOrNull { it.mode == CallAudioMode.DEFAULT_TONE }
            CallSessionSetup(mode, "dialing", config, variant, mediaId ?: fallback?.id)
        }
    }

    suspend fun addMedia(entry: CallMediaEntry) {
        dao.saveMediaEntry(entry.toEntity())
    }

    suspend fun deleteMedia(id: String): CallMediaEntry? {
        val media = dao.getMediaEntry(id)?.toCallMediaEntry() ?: return null
        dao.deleteMediaEntry(id)
        return media
    }

    suspend fun updateNumberMode(number: String, mode: CallAudioMode) = numberBindingsMutex.withLock {
        val normalized = normalizePhoneNumber(number)
        database.withTransaction {
            val previous = dao.getNumberPool(normalized)
            dao.saveNumberPool(CallNumberPoolEntity(normalized, mode.name,
                waitingMediaId = previous?.waitingMediaId.takeIf { previous?.mode == mode.name }))
        }
    }

    suspend fun addNumberToPool(number: String) {
        val normalized = normalizePhoneNumber(number)
        if (normalized.length !in 3..20) return
        val defaultMode = configuration().selectedAudioMode.name
        database.withTransaction {
            if (dao.getAllNumberPools().none { normalizePhoneNumber(it.number) == normalized }) {
                dao.saveNumberPool(CallNumberPoolEntity(normalized, defaultMode))
            }
        }
    }

    suspend fun ensureConfiguredNumbers(numbers: Set<String>) {
        val defaultMode = configuration().selectedAudioMode.name
        database.withTransaction {
            val existing = dao.getAllNumberPools().map { normalizePhoneNumber(it.number) }.toMutableSet()
            numbers.map(::normalizePhoneNumber).filter { it.length in 3..20 }.distinct().forEach { number ->
                if (existing.add(number)) dao.saveNumberPool(CallNumberPoolEntity(number, defaultMode))
            }
        }
    }

    suspend fun add(call: ManualCall) {
        importLegacyRecords()
        dao.insert(call.toEntity())
        appContext.contentResolver.notifyChange(CallRecordContract.CallRecord.CONTENT_URI, null)
    }

    internal fun recordingFile(name: String): File = File(recordingsDir, name)

    internal suspend fun saveRecording(id: String, source: Uri?, displayDurationMillis: Long?) =
        recordingsMutex.withLock {
            withContext(Dispatchers.IO) {
                val previous = checkNotNull(dao.getCall(id)) { "通话记录不存在" }
                var imported: File? = null
                var saved = false
                try {
                    val actualDuration = if (source != null) {
                        check(recordingsDir.isDirectory || recordingsDir.mkdirs()) { "无法创建录音目录" }
                        val destination = recordingFile(UUID.randomUUID().toString())
                        imported = destination
                        val input = checkNotNull(appContext.contentResolver.openInputStream(source)) { "无法读取录音文件" }
                        input.use { stream ->
                            destination.outputStream().use { output ->
                                val buffer = ByteArray(64 * 1024)
                                while (true) {
                                    ensureActive()
                                    val size = stream.read(buffer)
                                    if (size < 0) break
                                    output.write(buffer, 0, size)
                                }
                            }
                        }
                        val retriever = MediaMetadataRetriever()
                        try {
                            retriever.setDataSource(destination.absolutePath)
                            check(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes")
                            checkNotNull(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.takeIf { it > 0 })
                        } catch (error: Exception) {
                            throw IllegalArgumentException("无法读取录音，请选择可播放的音频文件", error)
                        } finally {
                            retriever.release()
                        }
                    } else previous.recordingDurationMillis
                    val duration = displayDurationMillis ?: actualDuration
                    val name = imported?.name ?: previous.recordingPath
                    require(duration > 0) { "请选择录音文件或填写显示时长" }
                    withContext(NonCancellable) {
                        check(dao.updateRecording(id, name, duration) == 1) { "通话记录不存在" }
                        saved = true
                        if (imported != null) previous.recordingPath?.let { recordingFile(it).delete() }
                    }
                } finally {
                    if (!saved) imported?.delete()
                }
            }
        }

    suspend fun delete(record: CallRecord) {
        record.storedId?.let {
            deleteStoredRecords(listOf(it))
            appContext.contentResolver.notifyChange(CallRecordContract.CallRecord.CONTENT_URI, null)
        }
        record.demoIndex?.let { dao.hideDemo(HiddenDemoCallEntity(it)) }
    }

    suspend fun clearAll() {
        recordingsMutex.withLock {
            withContext(Dispatchers.IO + NonCancellable) {
                dao.deleteAllCalls()
                recordingsDir.listFiles()?.forEach { it.delete() }
            }
        }
        appContext.contentResolver.notifyChange(CallRecordContract.CallRecord.CONTENT_URI, null)
    }

    suspend fun deleteRecords(records: List<CallRecord>) {
        deleteStoredRecords(records.mapNotNull { it.storedId }.distinct(), records.mapNotNull { it.demoIndex }.distinct())
        appContext.contentResolver.notifyChange(CallRecordContract.CallRecord.CONTENT_URI, null)
    }

    private suspend fun deleteStoredRecords(ids: List<String>, demos: List<Int> = emptyList()) = recordingsMutex.withLock {
        withContext(Dispatchers.IO + NonCancellable) {
            val files = database.withTransaction {
                ids.mapNotNull { id -> dao.getCall(id)?.recordingPath.also { dao.deleteById(id) } }
                    .also { demos.forEach { dao.hideDemo(HiddenDemoCallEntity(it)) } }
            }
            files.forEach { recordingFile(it).delete() }
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
            if (rows.isNotEmpty()) appContext.contentResolver.notifyChange(CallRecordContract.CallRecord.CONTENT_URI, null)
        }
    }
}

internal fun openManualCallDatabase(context: Context): ManualCallDatabase =
    Room.databaseBuilder(context.applicationContext, ManualCallDatabase::class.java, "manual_calls.db")
        .addMigrations(ManualCallMigration1To2, ManualCallMigration2To3, ManualCallMigration3To4, ManualCallMigration4To5, ManualCallMigration5To6, ManualCallMigration6To7)
        .build()

@Entity(tableName = "manual_calls")
data class ManualCallEntity(
    @PrimaryKey val id: String,
    val number: String,
    val kind: String,
    val durationSeconds: Int,
    val timeMillis: Long,
    val wasConnected: Boolean,
    val attribution: String?,
    val operator: String?,
    val connectedTimeMillis: Long,
    val endTimeMillis: Long,
    val callNumber: Int,
    val recordingPath: String?,
    @ColumnInfo(defaultValue = "0") val recordingDurationMillis: Long,
)

@Entity(tableName = "hidden_demo_calls")
data class HiddenDemoCallEntity(@PrimaryKey val demoIndex: Int)

@Entity(tableName = "call_rule_config")
data class CallRuleConfigEntity(@PrimaryKey val id: Int = 1, val content: String)

@Entity(tableName = "call_number_pools")
data class CallNumberPoolEntity(
    @PrimaryKey val number: String,
    val mode: String,
    val waitingMediaId: String? = null,
)

data class CallTransferState(
    val configurationJson: String,
    val mediaEntries: List<CallMediaEntry>,
)

@Entity(tableName = "call_media")
data class CallMediaEntity(
    @PrimaryKey val id: String, val mode: String, val title: String, val filePath: String, val mimeType: String, val iconPath: String?,
)

private fun ManualCall.toEntity() = ManualCallEntity(
    id, number, kind.name, durationSeconds, timeMillis, wasConnected,
    attribution, operator, connectedTimeMillis, endTimeMillis, callNumber,
    recordingPath, recordingDurationMillis,
)

private fun ManualCallEntity.toManualCall() = ManualCall(
    id, number, CallKind.valueOf(kind), durationSeconds, timeMillis, wasConnected,
    attribution, operator, connectedTimeMillis, endTimeMillis, callNumber,
    recordingPath, recordingDurationMillis,
)
private fun CallMediaEntry.toEntity() = CallMediaEntity(id, mode.name, title, filePath, mimeType, iconPath)
private fun CallMediaEntity.toCallMediaEntry() = CallMediaEntry(id, CallAudioMode.fromStored(mode), title, filePath, mimeType, iconPath)

private fun normalizeStatuses(statuses: List<CallStatusRule>): List<CallStatusRule> {
    val validIds = statuses.map(CallStatusRule::id).toSet()
    return statuses.map { status -> status.copy(
        durationSeconds = status.durationSeconds.coerceIn(0, 86_400),
        audioPlayCount = status.audioPlayCount.coerceIn(0, 99),
        timeoutTargetId = status.timeoutTargetId?.takeIf { it in validIds },
        buttons = status.buttons.map { button -> button.copy(
            visible = true,
            targetStatusId = button.targetStatusId?.takeIf { it in validIds },
        ) },
    ) }
}

private fun normalizeConfiguration(configuration: CallFlowConfiguration, previous: CallFlowConfiguration): CallFlowConfiguration {
    val complete = configuration.withRequiredCallStatuses(previous).withProgrammedCallFlow()
    return complete.copy(
        initialStatusId = complete.initialStatusId.takeIf { id -> complete.statuses.any { it.id == id } } ?: "dialing",
        statuses = normalizeStatuses(complete.statuses),
    )
}

private fun encodeConfiguration(config: CallFlowConfiguration): String = JSONObject().apply {
    put("initialStatusId", config.initialStatusId)
    put("rulesVersion", 2)
    put("selectedAudioMode", config.selectedAudioMode.name)
    put("statuses", encodeStatuses(config.statuses))
}.toString()

private fun encodeStatuses(statuses: List<CallStatusRule>): JSONArray = JSONArray().apply {
        statuses.forEach { status -> put(JSONObject().apply {
            put("id", status.id); put("name", status.name); put("stage", status.stage.name)
            put("durationSeconds", status.durationSeconds); put("timeoutTargetId", status.timeoutTargetId ?: JSONObject.NULL)
            put("outcome", status.outcome ?: JSONObject.NULL)
            put("mediaEntryId", status.mediaEntryId ?: JSONObject.NULL)
            put("soundEntryId", status.soundEntryId ?: JSONObject.NULL)
            put("playSoundOnce", status.playSoundOnce)
            put("soundMuted", status.soundMuted)
            put("audioPlayCount", status.audioPlayCount)
            if (status.id == "waiting") put("useDefaultPromptWait", status.useDefaultPromptWait)
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
}

private fun decodeConfiguration(raw: String): CallFlowConfiguration = runCatching {
    val root = JSONObject(raw)
    val legacy = root.optInt("rulesVersion", 1) < 2
    val statuses = decodeStatuses(root.optJSONArray("statuses") ?: JSONArray(), legacy)
    val avatarStatuses = root.optJSONArray("avatarStatuses")?.let { decodeStatuses(it, legacy) }.orEmpty()
    val configuration = CallFlowConfiguration(
        selectedAudioMode = CallAudioMode.fromStored(root.optString("selectedAudioMode")),
        statuses = statuses,
    ).withProgrammedCallFlow()
    if (legacy) {
        val avatar = CallFlowConfiguration(statuses = avatarStatuses).withProgrammedCallFlow()
        configuration.copy(statuses = mergeLegacyCallStatuses(configuration.statuses, avatar.statuses))
            .withProgrammedCallFlow()
    } else configuration.withRequiredCallStatuses(configuration).withProgrammedCallFlow()
}.getOrElse { CallFlowConfiguration().withProgrammedCallFlow() }

private fun decodeStatuses(statusesJson: JSONArray, legacy: Boolean): List<CallStatusRule> {
    val statuses = buildList {
        for (i in 0 until statusesJson.length()) {
            val item = statusesJson.optJSONObject(i) ?: continue
            val stage = CallStage.entries.firstOrNull { it.name == item.optString("stage") } ?: CallStage.WAITING
            val buttonsJson = item.optJSONArray("buttons") ?: JSONArray()
            val storedButtons = buildList {
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
            val wasLegacyOutgoingDefaults = stage in setOf(CallStage.DIALING, CallStage.WAITING) &&
                storedButtons.size == CallButtonId.entries.size && storedButtons.all { button ->
                    button.visible && button.enabled == (button.id == CallButtonId.SPEAKER || button.id == CallButtonId.HANGUP) &&
                        button.targetStatusId == null && button.label == null && button.visualStyle == CallVisualStyle()
                }
            val buttons = if (wasLegacyOutgoingDefaults) com.lastcoffee.top.oppo_call_compose.model.defaultButtons(stage) else storedButtons
            val id = item.optString("id", "status-$i")
            val duration = item.optInt("durationSeconds", 0)
            add(CallStatusRule(
                id = id, name = item.optString("name", "状态 ${i + 1}"), stage = stage,
                durationSeconds = duration, timeoutTargetId = item.optString("timeoutTargetId").takeIf { it.isNotBlank() && it != "null" },
                buttons = buttons, outcome = item.optString("outcome").takeIf { it.isNotBlank() && it != "null" },
                mediaEntryId = item.optString("mediaEntryId").takeIf { it.isNotBlank() && it != "null" },
                soundEntryId = item.optString("soundEntryId").takeIf { it.isNotBlank() && it != "null" },
                playSoundOnce = item.optBoolean("playSoundOnce", false),
                soundMuted = item.optBoolean("soundMuted", false),
                audioPlayCount = item.optInt("audioPlayCount", 0).coerceIn(0, 99),
                // Older shared rules inferred the short prompt wait from the 8-second duration.
                useDefaultPromptWait = id == "waiting" && item.optBoolean(
                    "useDefaultPromptWait", !legacy && duration == DEFAULT_WAITING_SECONDS,
                ),
                callerNameStyle = item.optJSONObject("callerNameStyle")?.toVisualStyle() ?: CallVisualStyle(),
                callerNumberStyle = item.optJSONObject("callerNumberStyle")?.toVisualStyle() ?: CallVisualStyle(),
                locationTextStyle = item.optJSONObject("locationTextStyle")?.toVisualStyle() ?: CallVisualStyle(),
                statusTextStyle = item.optJSONObject("statusTextStyle")?.toVisualStyle() ?: CallVisualStyle(),
            ).withVisibleCallButtons().let { if (legacy) it.withDefaultStatusAudio() else it })
        }
    }
    return statuses
}

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

private val ManualCallMigration4To5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE manual_calls ADD COLUMN attribution TEXT")
        db.execSQL("ALTER TABLE manual_calls ADD COLUMN operator TEXT")
        db.execSQL("ALTER TABLE manual_calls ADD COLUMN connectedTimeMillis INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE manual_calls ADD COLUMN endTimeMillis INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE manual_calls ADD COLUMN callNumber INTEGER NOT NULL DEFAULT 0")
    }
}

private val ManualCallMigration5To6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE manual_calls ADD COLUMN recordingPath TEXT")
        db.execSQL("ALTER TABLE manual_calls ADD COLUMN recordingDurationMillis INTEGER NOT NULL DEFAULT 0")
    }
}

private val ManualCallMigration6To7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE call_number_pools ADD COLUMN waitingMediaId TEXT")
    }
}

@Dao
interface ManualCallDao {
    @Query("SELECT * FROM manual_calls ORDER BY timeMillis DESC, id DESC")
    fun observeAll(): Flow<List<ManualCallEntity>>

    @Query("SELECT * FROM manual_calls WHERE id = :id")
    suspend fun getCall(id: String): ManualCallEntity?

    @Query("UPDATE manual_calls SET recordingPath = :path, recordingDurationMillis = :duration WHERE id = :id")
    suspend fun updateRecording(id: String, path: String?, duration: Long): Int

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

    @Query("DELETE FROM call_number_pools WHERE number = :number")
    suspend fun deleteNumberPool(number: String)

    @Query("SELECT * FROM call_media ORDER BY title COLLATE NOCASE")
    fun observeMediaEntries(): Flow<List<CallMediaEntity>>

    @Query("SELECT * FROM call_media WHERE id = :id LIMIT 1")
    suspend fun getMediaEntry(id: String): CallMediaEntity?

    @Query("SELECT * FROM call_media")
    suspend fun getAllMediaEntries(): List<CallMediaEntity>

    @Query("DELETE FROM call_media")
    suspend fun deleteAllMediaEntries()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveMediaEntry(entry: CallMediaEntity)

    @Query("DELETE FROM call_media WHERE id = :id")
    suspend fun deleteMediaEntry(id: String)
}

@Database(entities = [ManualCallEntity::class, HiddenDemoCallEntity::class, CallRuleConfigEntity::class, CallNumberPoolEntity::class, CallMediaEntity::class], version = 7, exportSchema = true)
abstract class ManualCallDatabase : RoomDatabase() {
    abstract fun manualCallDao(): ManualCallDao
}
