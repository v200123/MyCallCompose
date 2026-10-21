package com.lastcoffee.top.oppo_call_compose.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import me.ihxq.projects.pna.PhoneNumberLookup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PhoneAttributionRuntimeTest {
    @Test
    fun packagedLookupInitializesAndCachesLocalAttribution() = runBlocking {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val preferencePrefix = "attribution_runtime_test_${UUID.randomUUID()}_"
        val isolatedContext = object : ContextWrapper(appContext) {
            override fun getApplicationContext(): Context = this

            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                super.getSharedPreferences(preferencePrefix + name, mode)
        }
        try {
            // Initialize the actual library inside Android, before any cache can bypass it.
            // This fails at LoggerFactory when the runtime dependency is missing.
            val info = PhoneNumberLookup().lookup("13800138000").orElseThrow {
                AssertionError("The packaged phone.dat must contain prefix 1380013")
            }
            assertEquals("北京", info.attribution.province)
            assertEquals("移动", info.isp.cnName.removePrefix("中国"))

            val store = PhoneAttributionStore(isolatedContext)
            assertNull(store.locationFor("13800138000"))
            store.request("13800138000", PhoneExtras())
            assertEquals("北京 移动", store.locationFor("13800138000"))
            assertEquals("北京 移动", store.locationFor("13800139999"))

            // A new store must restore the same local result from its persistent cache.
            val restored = PhoneAttributionStore(isolatedContext)
            assertEquals("北京 移动", restored.locationFor("+86 13800138000"))
        } finally {
            appContext.deleteSharedPreferences(preferencePrefix + "phone_attributions")
        }
    }
}
