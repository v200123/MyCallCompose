package com.example.appupdater

import kotlinx.serialization.Serializable

@Serializable
data class UpdateCheckResult(
    val needUpdate: Boolean = false,
    val forceUpdate: Boolean = false,
    val latestVersion: String? = null,
    val latestVersionCode: Int? = null,
    val updateLog: String? = null,
    val downloadUrl: String? = null,
    val fileSize: Long? = null,
    val md5: String? = null
)
