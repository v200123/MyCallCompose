package com.lastcoffee.top.oppo_call_compose.data

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.net.Uri
import androidx.sqlite.db.SimpleSQLiteQuery

/** Exposes local call records with telephoneHelp's Cursor column names and types. */
class CallRecordProvider : ContentProvider() {
    private lateinit var database: ManualCallDatabase

    override fun onCreate(): Boolean {
        database = openManualCallDatabase(context ?: return false)
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val match = matcher.match(uri)
        require(match == RECORDS || match == RECORD_ID) { "Unknown URI: $uri" }
        val columns = projection?.takeIf { it.isNotEmpty() }?.toList() ?: ALL_COLUMNS
        require(columns.all { it in ALL_COLUMNS }) { "Unknown call record column" }

        val where = selection?.takeIf { it.isNotBlank() }
        require(where == null || isSafeSelection(where)) { "Unsupported selection: $selection" }
        val args = selectionArgs?.toList().orEmpty()
        require((where?.count { it == '?' } ?: 0) == args.size) { "Selection argument count mismatch" }
        val order = sortOrder?.takeIf { it.isNotBlank() } ?: CallRecordContract.CallRecord.DEFAULT_SORT_ORDER
        require(SAFE_SORT.matches(order)) { "Unsupported sort order: $sortOrder" }

        val sql = buildString {
            append("SELECT ")
            append(columns.joinToString(", "))
            append(" FROM (")
            append(EXPORT_VIEW)
            append(") AS shared_calls")
            val conditions = mutableListOf<String>()
            if (match == RECORD_ID) conditions += "id = ?"
            if (where != null) conditions += "($where)"
            if (conditions.isNotEmpty()) append(" WHERE ${conditions.joinToString(" AND ")}")
            append(" ORDER BY $order")
        }
        val argumentFields = where?.let { ARGUMENT_FIELD.findAll(it).map { match -> match.groupValues[1] }.toList() }.orEmpty()
        require(argumentFields.size == args.size) { "Unsupported selection arguments" }
        val boundArgs = args.mapIndexed { index, value ->
            val field = argumentFields[index]
            if (NUMERIC_COLUMNS.any { it.equals(field, true) }) {
                value.toLongOrNull() ?: throw IllegalArgumentException("Invalid numeric argument for $field")
            } else value
        }
        val queryArgs: List<Any> = (if (match == RECORD_ID) listOf<Any>(uri.lastPathSegment!!.toLong()) else emptyList()) + boundArgs
        return database.openHelper.readableDatabase.query(SimpleSQLiteQuery(sql, queryArgs.toTypedArray())).apply {
            setNotificationUri(requireNotNull(context).contentResolver, CallRecordContract.CallRecord.CONTENT_URI)
        }
    }

    override fun getType(uri: Uri): String = when (matcher.match(uri)) {
        RECORDS -> CallRecordContract.CallRecord.CONTENT_TYPE
        RECORD_ID -> CallRecordContract.CallRecord.CONTENT_ITEM_TYPE
        else -> throw IllegalArgumentException("Unknown URI: $uri")
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri =
        throw UnsupportedOperationException("Call records are read-only")

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("Call records are read-only")

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("Call records are read-only")

    companion object {
        private const val RECORDS = 1
        private const val RECORD_ID = 2
        private val matcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(CallRecordContract.CONTENT_AUTHORITY, CallRecordContract.CallRecord.PATH_CALL_RECORDS, RECORDS)
            addURI(CallRecordContract.CONTENT_AUTHORITY, "${CallRecordContract.CallRecord.PATH_CALL_RECORDS}/#", RECORD_ID)
        }

        private val ALL_COLUMNS = listOf(
            "_id", "id", "phoneNumber", "attribution", "operator", "startTime", "connectedTime",
            "endTime", "isConnected", "callNumber", "callType", "recordingPath",
            "recordingStartTime", "recordingEndTime",
        )
        private val NUMERIC_COLUMNS = setOf(
            "_id", "id", "startTime", "connectedTime", "endTime", "isConnected", "callNumber",
            "callType", "recordingStartTime", "recordingEndTime",
        )
        private const val FIELD = "(?:_id|id|phoneNumber|attribution|operator|startTime|connectedTime|endTime|isConnected|callNumber|callType|recordingPath|recordingStartTime|recordingEndTime)"
        private val SELECTION_TOKEN = Regex("\\s+|[()]|\\?|>=|<=|!=|=|>|<|[A-Za-z_][A-Za-z0-9_]*|[0-9]+")
        private val ARGUMENT_FIELD = Regex("(?i)\\b([A-Za-z_][A-Za-z0-9_]*)\\s*(?:=|!=|>=|<=|>|<|LIKE)\\s*\\?")
        private val SAFE_SORT = Regex("^\\s*$FIELD(?:\\s+(?:ASC|DESC))?(?:\\s*,\\s*$FIELD(?:\\s+(?:ASC|DESC))?)*\\s*$", RegexOption.IGNORE_CASE)

        private fun isSafeSelection(selection: String): Boolean {
            var position = 0
            for (match in SELECTION_TOKEN.findAll(selection)) {
                if (match.range.first != position) return false
                val token = match.value
                if (token.first().isLetter() && ALL_COLUMNS.none { it.equals(token, true) } && !token.equals("AND", true) &&
                    !token.equals("OR", true) && !token.equals("LIKE", true)) return false
                position = match.range.last + 1
            }
            return position == selection.length
        }

        // Keep the telephoneHelp column contract, including fields entered on the add-record screen.
        private const val EXPORT_VIEW = """
            SELECT rowid AS _id, rowid AS id, number AS phoneNumber,
                   attribution, operator,
                   timeMillis AS startTime,
                   CASE WHEN wasConnected = 1 AND kind NOT IN ('MISSED', 'BLOCKED') THEN COALESCE(NULLIF(connectedTimeMillis, 0), timeMillis) ELSE 0 END AS connectedTime,
                   COALESCE(NULLIF(endTimeMillis, 0), timeMillis + CASE WHEN wasConnected = 1 AND kind NOT IN ('MISSED', 'BLOCKED') THEN durationSeconds * 1000 ELSE 0 END) AS endTime,
                   CASE WHEN wasConnected = 1 AND kind NOT IN ('MISSED', 'BLOCKED') THEN 1 ELSE 0 END AS isConnected,
                   callNumber,
                   CASE WHEN kind IN ('INCOMING', 'MISSED', 'BLOCKED') THEN 1 ELSE 0 END AS callType,
                   NULL AS recordingPath, 0 AS recordingStartTime, 0 AS recordingEndTime
            FROM manual_calls
        """
    }
}
