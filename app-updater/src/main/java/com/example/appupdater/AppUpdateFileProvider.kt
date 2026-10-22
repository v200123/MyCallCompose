package com.example.appupdater

import androidx.core.content.FileProvider

/** 使用独立类型，避免与宿主的配置包分享 Provider 在清单合并时冲突。 */
class AppUpdateFileProvider : FileProvider()
