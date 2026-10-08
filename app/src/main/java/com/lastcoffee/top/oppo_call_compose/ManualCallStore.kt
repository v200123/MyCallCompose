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
import androidx.room.migration.Migration
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
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
    val wasConnected: Boolean = true,
) {
    fun toCallRecord(): CallRecord {
        val time = Instant.ofEpochMilli(timeMillis).atZone(ZoneId.systemDefault())
        val type = kind.displayLabel()
        val detail = if (kind == CallKind.MISSED || kind == CallKind.BLOCKED ||
            (kind == CallKind.OUTGOING && !wasConnected)
        ) "未接通" else formatCallDuration(durationSeconds)
        return CallRecord(
            number = number,
            description = "$type  $detail",
            date = time.format(DateTimeFormatter.ofPattern("yyyy/M/d")),
            kind = kind,
            detailTime = time.format(DateTimeFormatter.ofPattern("yyyy/M/d HH:mm")),
            durationDetail = "$type  $detail",
            storedId = id,
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
    private val database = Room.databaseBuilder(appContext, ManualCallDatabase::class.java, "manual_calls.db")
        .addMigrations(ManualCallMigration1To2, ManualCallMigration2To3)
        .build()
    private val dao = database.manualCallDao()
    private val importMutex = Mutex()

    val calls: Flow<List<ManualCall>> = dao.observeAll().map { rows -> rows.map { it.toManualCall() } }
    val hiddenDemoIndexes: Flow<List<Int>> = dao.observeHiddenDemoIndexes()

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

private fun ManualCall.toEntity() = ManualCallEntity(id, number, kind.name, durationSeconds, timeMillis, wasConnected)

private fun ManualCallEntity.toManualCall() = ManualCall(id, number, CallKind.valueOf(kind), durationSeconds, timeMillis, wasConnected)

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
}

@Database(entities = [ManualCallEntity::class, HiddenDemoCallEntity::class], version = 3, exportSchema = true)
abstract class ManualCallDatabase : RoomDatabase() {
    abstract fun manualCallDao(): ManualCallDao
}
