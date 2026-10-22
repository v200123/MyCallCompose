package com.example.appupdater

data class UpdateConfig(
    val baseUrl: String,
    val checkPath: String = "/api/check",
    val connectTimeoutMillis: Long = 15_000L,
    val readTimeoutMillis: Long = 15_000L
) {
    init {
        require(baseUrl.isNotBlank()) { "升级服务地址不能为空" }
        require(checkPath.isNotBlank()) { "检查更新接口路径不能为空" }
        require(connectTimeoutMillis > 0L) { "连接超时时间必须大于 0" }
        require(readTimeoutMillis > 0L) { "读取超时时间必须大于 0" }
    }
}
