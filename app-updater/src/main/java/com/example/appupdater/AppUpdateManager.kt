package com.example.appupdater

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

internal class AppUpdateManager(
    private val config: UpdateConfig
) {
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(config.connectTimeoutMillis, TimeUnit.MILLISECONDS)
        .readTimeout(config.readTimeoutMillis, TimeUnit.MILLISECONDS)
        .build()
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
    private val updateApi: UpdateApi = Retrofit.Builder()
        .baseUrl(config.baseUrl.trimEnd('/') + "/")
        .client(httpClient)
        .addConverterFactory(json.asConverterFactory(JSON_MEDIA_TYPE.toMediaType()))
        .build()
        .create(UpdateApi::class.java)

    fun checkForUpdate(context: Context): UpdateCheckResult? {
        return checkForUpdate(context.packageName, getCurrentVersionCode(context))
    }

    internal fun checkForUpdate(packageName: String, versionCode: Long): UpdateCheckResult? {
        val call = updateApi.checkForUpdate(
            url = config.checkPath.ensureStartsWithSlash(),
            packageName = packageName,
            versionCode = versionCode
        )
        val requestUrl = call.request().url.toString()

        logRequestStart("检查更新", requestUrl)
        return try {
            val response = call.execute()
            val result = response.body()
            logCheckResponse(response, result)
            if (response.isSuccessful) {
                result
            } else {
                response.errorBody()?.close()
                null
            }
        } catch (throwable: Throwable) {
            Log.e(TAG, "检查更新请求失败: url=$requestUrl", throwable)
            null
        }
    }

    fun downloadApk(
        context: Context,
        result: UpdateCheckResult,
        onProgressChanged: ((Int) -> Unit)? = null
    ): File {
        val storageRoot = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: context.filesDir
        return downloadApk(context.packageName, storageRoot, result, onProgressChanged)
    }

    internal fun downloadApk(
        packageName: String,
        storageRoot: File,
        result: UpdateCheckResult,
        onProgressChanged: ((Int) -> Unit)? = null
    ): File {
        val targetVersionCode = result.latestVersionCode
            ?: throw IllegalStateException("缺少最新版本号，无法下载升级包")
        val resolvedUrl = resolveDownloadUrl(result.downloadUrl)
            ?: throw IllegalStateException("缺少下载地址，无法下载升级包")
        val targetDirectory = File(storageRoot, UPDATE_DIRECTORY_NAME)
        if (!targetDirectory.exists() && !targetDirectory.mkdirs()) {
            throw IllegalStateException("无法创建升级包目录")
        }
        val targetFile = File(
            targetDirectory,
            "update_${packageName}_$targetVersionCode.apk"
        )

        if (targetFile.exists() && verifyDownloadedFile(targetFile, result)) {
            Log.d(TAG, "升级包复用本地缓存: path=${targetFile.absolutePath}, size=${targetFile.length()}")
            onProgressChanged?.invoke(100)
            return targetFile
        }

        val tempFile = File(targetDirectory, "${targetFile.name}.download")
        if (tempFile.exists()) {
            tempFile.delete()
        }

        val call = updateApi.downloadApk(resolvedUrl)
        val requestUrl = call.request().url.toString()

        logRequestStart("下载升级包", requestUrl)
        try {
            val response = call.execute()
            logDownloadResponse(response)
            if (!response.isSuccessful) {
                response.errorBody()?.close()
                throw IllegalStateException("下载升级包失败，HTTP状态码: ${response.code()}")
            }

            val body = response.body()
                ?: throw IllegalStateException("下载升级包失败，响应体为空")
            body.use {
                val expectedSize = result.fileSize ?: body.contentLength().takeIf { it > 0L }
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var downloadedBytes = 0L
                var lastProgress = -1
                var lastLoggedProgressBucket = -1

                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        while (true) {
                            val readSize = input.read(buffer)
                            if (readSize <= 0) break
                            output.write(buffer, 0, readSize)
                            downloadedBytes += readSize
                            if (expectedSize != null && expectedSize > 0L) {
                                val progress = ((downloadedBytes * 100) / expectedSize)
                                    .toInt()
                                    .coerceIn(0, 100)
                                if (progress != lastProgress) {
                                    lastProgress = progress
                                    onProgressChanged?.invoke(progress)
                                }
                                val progressBucket = progress / 10
                                if (progressBucket != lastLoggedProgressBucket) {
                                    lastLoggedProgressBucket = progressBucket
                                    Log.d(
                                        TAG,
                                        "下载升级包进度: $progress%, downloaded=$downloadedBytes, total=$expectedSize"
                                    )
                                }
                            }
                        }
                        output.flush()
                    }
                }
            }

            if (targetFile.exists()) {
                targetFile.delete()
            }
            if (!tempFile.renameTo(targetFile)) {
                throw IllegalStateException("升级包写入本地失败")
            }

            if (!verifyDownloadedFile(targetFile, result)) {
                targetFile.delete()
                throw IllegalStateException("下载完成，但升级包校验失败")
            }

            Log.d(
                TAG,
                "下载升级包完成: path=${targetFile.absolutePath}, size=${targetFile.length()}, md5=${result.md5.orEmpty()}"
            )
            onProgressChanged?.invoke(100)
            return targetFile
        } catch (throwable: Throwable) {
            Log.e(TAG, "下载升级包请求失败: url=$requestUrl", throwable)
            throw throwable
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    fun canRequestPackageInstalls(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()
    }

    fun createInstallPermissionIntent(context: Context): Intent {
        return Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        )
    }

    fun installApk(context: Context, apkFile: File) {
        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.appupdater.fileprovider",
            apkFile
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(installIntent)
    }

    private fun resolveDownloadUrl(downloadUrl: String?): String? {
        if (downloadUrl.isNullOrBlank()) return null
        if (downloadUrl.startsWith("http://") || downloadUrl.startsWith("https://")) {
            return downloadUrl
        }
        return config.baseUrl.toHttpUrl()
            .resolve(downloadUrl.ensureStartsWithSlash())
            ?.toString()
    }

    private fun verifyDownloadedFile(file: File, result: UpdateCheckResult): Boolean {
        if (!file.exists() || file.length() <= 0L) return false
        val expectedSize = result.fileSize
        if (expectedSize != null && expectedSize > 0L && file.length() != expectedSize) {
            Log.w(TAG, "升级包大小校验失败: expected=$expectedSize, actual=${file.length()}")
            return false
        }
        val expectedMd5 = result.md5
        if (!expectedMd5.isNullOrBlank()) {
            val actualMd5 = calculateMd5(file)
            if (!actualMd5.equals(expectedMd5, ignoreCase = true)) {
                Log.w(TAG, "升级包MD5校验失败: expected=$expectedMd5, actual=$actualMd5")
                return false
            }
        }
        return true
    }

    private fun calculateMd5(file: File): String {
        val digest = MessageDigest.getInstance("MD5")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val readSize = input.read(buffer)
                if (readSize <= 0) break
                digest.update(buffer, 0, readSize)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun getCurrentVersionCode(context: Context): Long {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(0L)
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        return PackageInfoCompat.getLongVersionCode(packageInfo)
    }

    private fun logRequestStart(scene: String, url: String) {
        Log.d(TAG, "$scene 请求开始: method=GET, url=$url")
    }

    private fun logCheckResponse(
        response: Response<UpdateCheckResult>,
        body: UpdateCheckResult?
    ) {
        Log.d(
            TAG,
            "检查更新响应: code=${response.code()}, successful=${response.isSuccessful}, message=${response.message()}, body=${body.toString().take(LOG_BODY_MAX_LENGTH)}"
        )
    }

    private fun logDownloadResponse(response: Response<ResponseBody>) {
        Log.d(
            TAG,
            "下载升级包响应: code=${response.code()}, successful=${response.isSuccessful}, message=${response.message()}, contentLength=${response.body()?.contentLength() ?: -1L}"
        )
    }

    private fun String.ensureStartsWithSlash(): String {
        return if (startsWith("/")) this else "/$this"
    }

    private companion object {
        const val TAG = "AppUpdateManager"
        const val LOG_BODY_MAX_LENGTH = 2_000
        const val UPDATE_DIRECTORY_NAME = "app-updater"
        const val JSON_MEDIA_TYPE = "application/json"
    }
}
