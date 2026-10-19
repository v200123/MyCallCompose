package com.lastcoffee.top.oppo_call_compose.data

import android.net.Uri

/** Same call-record column contract as telephoneHelp, with this app's own authority. */
object CallRecordContract {
    const val CONTENT_AUTHORITY = "com.lastcoffee.top.oppo_call_compose.provider"
    const val PERMISSION_READ = "com.lastcoffee.top.oppo_call_compose.permission.READ_CALL_RECORDS"
    @JvmField val BASE_CONTENT_URI: Uri = Uri.parse("content://$CONTENT_AUTHORITY")

    object CallRecord {
        const val PATH_CALL_RECORDS = "callrecords"
        @JvmField val CONTENT_URI: Uri = BASE_CONTENT_URI.buildUpon().appendPath(PATH_CALL_RECORDS).build()
        const val CONTENT_TYPE = "vnd.android.cursor.dir/vnd.telephonehelper.callrecord"
        const val CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.telephonehelper.callrecord"

        const val COLUMN_ID = "id"
        const val COLUMN_PHONE_NUMBER = "phoneNumber"
        const val COLUMN_ATTRIBUTION = "attribution"
        const val COLUMN_OPERATOR = "operator"
        const val COLUMN_START_TIME = "startTime"
        const val COLUMN_CONNECTED_TIME = "connectedTime"
        const val COLUMN_END_TIME = "endTime"
        const val COLUMN_IS_CONNECTED = "isConnected"
        const val COLUMN_CALL_NUMBER = "callNumber"
        const val COLUMN_CALL_TYPE = "callType"
        const val COLUMN_RECORDING_PATH = "recordingPath"
        const val COLUMN_RECORDING_START_TIME = "recordingStartTime"
        const val COLUMN_RECORDING_END_TIME = "recordingEndTime"
        const val DEFAULT_SORT_ORDER = "$COLUMN_START_TIME DESC"

        @JvmStatic fun buildCallRecordUri(id: Long): Uri =
            CONTENT_URI.buildUpon().appendPath(id.toString()).build()
    }
}
