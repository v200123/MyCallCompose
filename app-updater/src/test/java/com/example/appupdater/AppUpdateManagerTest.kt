package com.example.appupdater

import java.io.File
import java.security.MessageDigest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AppUpdateManagerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var manager: AppUpdateManager

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        manager = AppUpdateManager(UpdateConfig(baseUrl = server.url("/").toString()))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun checkUsesHostPackageAndVersionAndReadsForcedUpdate() {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(
            """{"needUpdate":true,"forceUpdate":true,"latestVersion":"1.1","latestVersionCode":2,"updateLog":"更新说明","downloadUrl":"/apk/phone.apk","extraField":"ignored"}""",
        ))

        val result = manager.checkForUpdate(PACKAGE_NAME, 1L)

        assertEquals(true, result?.needUpdate)
        assertEquals(true, result?.forceUpdate)
        assertEquals("1.1", result?.latestVersion)
        assertEquals(2, result?.latestVersionCode)
        assertEquals("更新说明", result?.updateLog)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/check", request.requestUrl?.encodedPath)
        assertEquals(PACKAGE_NAME, request.requestUrl?.queryParameter("packageName"))
        assertEquals("1", request.requestUrl?.queryParameter("versionCode"))
    }

    @Test
    fun noUpdateResponseRemainsDistinctFromCheckFailure() {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(
            """{"needUpdate":false,"forceUpdate":false,"latestVersion":null}""",
        ))
        server.enqueue(MockResponse().setResponseCode(503).setBody("unavailable"))

        assertEquals(false, manager.checkForUpdate(PACKAGE_NAME, 1L)?.needUpdate)
        assertNull(manager.checkForUpdate(PACKAGE_NAME, 1L))
    }

    @Test
    fun relativeDownloadPreservesQueryAndReusesVerifiedApk() {
        val bytes = "test apk contents".toByteArray()
        server.enqueue(MockResponse().setBody(String(bytes)))
        val result = UpdateCheckResult(
            needUpdate = true,
            latestVersionCode = 2,
            downloadUrl = "/apk/phone.apk?channel=stable",
            fileSize = bytes.size.toLong(),
            md5 = md5(bytes),
        )
        val progress = mutableListOf<Int>()

        val file = manager.downloadApk(PACKAGE_NAME, temporaryFolder.root, result, progress::add)

        assertArrayEquals(bytes, file.readBytes())
        assertEquals("/apk/phone.apk?channel=stable", server.takeRequest().path)
        assertEquals(100, progress.last())
        assertEquals(file, manager.downloadApk(PACKAGE_NAME, temporaryFolder.root, result))
        assertEquals(1, server.requestCount)
        assertFalse(File(file.parentFile, "${file.name}.download").exists())
    }

    @Test
    fun invalidChecksumRemovesApkAndPartialFile() {
        server.enqueue(MockResponse().setBody("incorrect apk"))

        assertThrows(IllegalStateException::class.java) {
            manager.downloadApk(
                PACKAGE_NAME,
                temporaryFolder.root,
                UpdateCheckResult(latestVersionCode = 2, downloadUrl = server.url("/apk").toString(), md5 = "invalid"),
            )
        }

        assertTrue(temporaryFolder.root.walkTopDown().none { it.isFile })
    }

    @Test
    fun incorrectFileSizeRejectsDownloadedApk() {
        server.enqueue(MockResponse().setBody("truncated"))

        assertThrows(IllegalStateException::class.java) {
            manager.downloadApk(
                PACKAGE_NAME,
                temporaryFolder.root,
                UpdateCheckResult(latestVersionCode = 2, downloadUrl = "/apk", fileSize = 100L),
            )
        }

        assertTrue(temporaryFolder.root.walkTopDown().none { it.isFile })
    }

    @Test
    fun downloadHttpFailureDoesNotLeaveInstallableFile() {
        server.enqueue(MockResponse().setResponseCode(404))

        assertThrows(IllegalStateException::class.java) {
            manager.downloadApk(
                PACKAGE_NAME,
                temporaryFolder.root,
                UpdateCheckResult(latestVersionCode = 2, downloadUrl = "/apk"),
            )
        }

        assertTrue(temporaryFolder.root.walkTopDown().none { it.isFile })
    }

    private fun md5(bytes: ByteArray): String = MessageDigest.getInstance("MD5")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    private companion object {
        const val PACKAGE_NAME = "com.lastcoffee.top.oppo_call_compose"
    }
}
