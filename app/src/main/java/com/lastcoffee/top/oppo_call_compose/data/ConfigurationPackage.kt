package com.lastcoffee.top.oppo_call_compose.data

import android.content.Context
import android.net.Uri
import com.lastcoffee.top.oppo_call_compose.model.CallAudioMode
import com.lastcoffee.top.oppo_call_compose.model.CallMediaEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class ConfigurationSummary(val rules: Int, val media: Int, val icons: Int, val numbers: Int)

class PreparedConfiguration internal constructor(
    val summary: ConfigurationSummary,
    internal val manifest: JSONObject,
    internal val stagingDirectory: File,
) {
    fun discard() { stagingDirectory.deleteRecursively() }
}

/** A portable package of call rules, number settings, ringback artwork, and media files. */
class ConfigurationPackage(
    context: Context,
    private val calls: ManualCallStore,
    private val extras: PhoneExtrasStore,
    private val badges: RingbackBadgeStore,
) {
    private val appContext = context.applicationContext
    private val archiveFileName = Regex("^(media|icons)/[0-9]+\\.[A-Za-z0-9]{1,8}$")
    private val maxManifestBytes = 4L * 1024 * 1024
    private val maxUnpackedBytes = 1_500_000_000L

    suspend fun exportTo(uri: Uri) = withContext(Dispatchers.IO) {
        val output = appContext.contentResolver.openOutputStream(uri, "wt") ?: error("无法创建配置包")
        output.use { writePackage(it) }
    }

    suspend fun createShareFile(): File = withContext(Dispatchers.IO) {
        val folder = File(appContext.cacheDir, "shared_config").apply { mkdirs() }
        folder.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86_400_000L }
            ?.forEach { it.delete() }
        val file = File(folder, "通话配置-${System.currentTimeMillis()}.zip")
        try {
            file.outputStream().use { writePackage(it) }
            file
        } catch (error: Throwable) {
            file.delete()
            throw error
        }
    }

    private suspend fun writePackage(output: OutputStream) {
        val state = calls.exportTransferState()
        val assetsRoot = File(appContext.filesDir, "call_media").canonicalFile
        val files = mutableListOf<Pair<String, File>>()
        val mediaJson = JSONArray()
        // Accept uploaded key sounds in older packages, but omit these unused files from new exports.
        state.mediaEntries.filter { it.mode != CallAudioMode.DIALPAD_TONE }.forEachIndexed { index, media ->
            fun asset(path: String, folder: String): String {
                val source = File(path).canonicalFile
                require(source.isFile && source.toPath().startsWith(assetsRoot.toPath())) {
                    "媒体文件不存在或不在应用目录：${media.title}"
                }
                val extension = source.extension.takeIf { it.matches(Regex("[A-Za-z0-9]{1,8}")) } ?: "bin"
                val archiveName = "$folder/$index.$extension"
                files += archiveName to source
                return archiveName
            }
            val fileName = asset(media.filePath, "media")
            val iconName = media.iconPath?.let { asset(it, "icons") }
            mediaJson.put(JSONObject().apply {
                put("id", media.id)
                put("mode", media.mode.name)
                put("title", media.title)
                put("mimeType", media.mimeType)
                put("file", fileName)
                put("icon", iconName ?: JSONObject.NULL)
            })
        }
        val manifest = JSONObject().apply {
            put("type", "oppo_phone_configuration")
            put("version", 1)
            put("callRules", JSONObject(state.configurationJson))
            put("phoneExtras", JSONObject(extras.exportSettings()))
            put("badgeRules", JSONObject(badges.exportSettings()))
            put("media", mediaJson)
        }
        ZipOutputStream(output.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifest.toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            files.forEach { (name, source) ->
                zip.putNextEntry(ZipEntry(name))
                source.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    suspend fun prepareImport(uri: Uri): PreparedConfiguration = withContext(Dispatchers.IO) {
        val staging = File(appContext.cacheDir, "config_import/${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val input = appContext.contentResolver.openInputStream(uri) ?: error("无法读取配置包")
            input.use { stream -> ZipInputStream(stream.buffered()).use { zip ->
                val first = zip.nextEntry ?: error("配置包为空")
                require(first.name == "manifest.json" && !first.isDirectory) { "配置包格式不正确" }
                val manifest = JSONObject(readLimited(zip, maxManifestBytes).toString(Charsets.UTF_8))
                require(manifest.optString("type") == "oppo_phone_configuration" && manifest.optInt("version") == 1) {
                    "配置包版本不支持"
                }
                val expected = expectedFiles(manifest)
                val seen = mutableSetOf<String>()
                var unpacked = 0L
                zip.closeEntry()
                while (true) {
                    val entry = zip.nextEntry ?: break
                    require(!entry.isDirectory && entry.name in expected && seen.add(entry.name)) { "配置包包含无效文件" }
                    val target = File(staging, entry.name)
                    target.parentFile?.mkdirs()
                    target.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val count = zip.read(buffer)
                            if (count < 0) break
                            unpacked += count
                            require(unpacked <= maxUnpackedBytes) { "配置包超过 1.5 GB 限制" }
                            output.write(buffer, 0, count)
                        }
                    }
                    zip.closeEntry()
                }
                require(seen == expected) { "配置包缺少音频、视频或图标文件" }
                PreparedConfiguration(summaryFor(manifest), manifest, staging)
            } }
        } catch (error: Throwable) {
            staging.deleteRecursively()
            throw error
        }
    }

    suspend fun applyImport(prepared: PreparedConfiguration) = withContext(Dispatchers.IO) {
        val root = prepared.manifest
        val previousExtras = extras.exportSettings()
        val previousBadges = badges.exportSettings()
        val previousMedia = calls.exportTransferState().mediaEntries
        val destination = File(appContext.filesDir, "call_media/packages/${UUID.randomUUID()}")
        var extrasChanged = false
        var badgesChanged = false
        try {
            val media = root.getJSONArray("media")
            val restored = (0 until media.length()).map { index ->
                val item = media.getJSONObject(index)
                val mode = CallAudioMode.entries.firstOrNull { it.name == item.getString("mode") }
                    ?: error("不支持的媒体类型")
                fun restore(name: String): String {
                    require(archiveFileName.matches(name)) { "媒体文件名无效" }
                    val source = File(prepared.stagingDirectory, name)
                    require(source.isFile) { "媒体文件缺失" }
                    val target = File(destination, name)
                    target.parentFile?.mkdirs()
                    source.copyTo(target)
                    return target.absolutePath
                }
                CallMediaEntry(
                    id = item.getString("id"), mode = mode, title = item.getString("title"),
                    filePath = restore(item.getString("file")), mimeType = item.getString("mimeType"),
                    iconPath = item.optString("icon").takeUnless { it.isBlank() || it == "null" }?.let(::restore),
                )
            }
            val replacement = CallTransferState(root.getJSONObject("callRules").toString(), restored)
            extras.replaceSettings(root.getJSONObject("phoneExtras").toString())
            extrasChanged = true
            badges.replaceSettings(root.getJSONObject("badgeRules").toString())
            badgesChanged = true
            calls.replaceTransferState(replacement)
        } catch (error: Throwable) {
            if (badgesChanged) runCatching { badges.replaceSettings(previousBadges) }
            if (extrasChanged) runCatching { extras.replaceSettings(previousExtras) }
            destination.deleteRecursively()
            throw error
        }
        runCatching { prepared.discard() }
        runCatching {
            val assetsRoot = File(appContext.filesDir, "call_media").canonicalFile.toPath()
            previousMedia.flatMap { listOfNotNull(it.filePath, it.iconPath) }.distinct().forEach { path ->
                val oldFile = File(path).canonicalFile
                if (oldFile.toPath().startsWith(assetsRoot)) oldFile.delete()
            }
        }
    }

    private fun expectedFiles(manifest: JSONObject): Set<String> {
        val extras = manifest.getJSONObject("phoneExtras")
        require(extras.has("locations") && extras.has("promptNumbers") && extras.has("ringbackBindings")) { "号码配置不完整" }
        val badges = manifest.getJSONObject("badgeRules")
        require(badges.has("rules") && badges.has("phoneBindings")) { "图标规则不完整" }
        require((manifest.getJSONObject("callRules").optJSONArray("statuses")?.length() ?: 0) > 0) { "通话规则为空" }
        val media = manifest.getJSONArray("media")
        require(media.length() <= 250) { "媒体文件数量过多" }
        val files = mutableSetOf<String>()
        for (index in 0 until media.length()) {
            val item = media.getJSONObject(index)
            val mode = CallAudioMode.entries.firstOrNull { it.name == item.getString("mode") }
            require(mode != null && mode.acceptsMimeType(item.getString("mimeType"))) { "媒体类型无效" }
            listOfNotNull(item.getString("file"), item.optString("icon").takeUnless { it.isBlank() || it == "null" })
                .forEach { name -> require(archiveFileName.matches(name) && files.add(name)) { "媒体文件名无效" } }
        }
        return files
    }

    private fun summaryFor(manifest: JSONObject): ConfigurationSummary {
        val extras = manifest.getJSONObject("phoneExtras")
        val badges = manifest.getJSONObject("badgeRules")
        val media = manifest.getJSONArray("media")
        val icons = (0 until media.length()).count { !media.getJSONObject(it).isNull("icon") }
        val numbers = extras.getJSONArray("locations").length() +
            extras.getJSONObject("ringbackBindings").length() +
            extras.optJSONObject("autoHangUpRules")?.length().orZero() +
            extras.getJSONObject("promptNumbers").length() +
            extras.optJSONObject("statusBindings")?.length().orZero() +
            badges.getJSONObject("phoneBindings").length()
        return ConfigurationSummary(manifest.getJSONObject("callRules").getJSONArray("statuses").length(), media.length(), icons, numbers)
    }

    private fun Int?.orZero(): Int = this ?: 0

    private fun readLimited(input: InputStream, limit: Long): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= limit) { "配置说明文件过大" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}
