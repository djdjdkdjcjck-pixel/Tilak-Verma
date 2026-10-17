package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.live.WakeWordDetector
import com.example.tools.ToolExecutionEngine
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Zoya Assistant", appName)
    }

    @Test
    fun `wake word detector can toggle state`() {
        var triggered = false
        val detector = WakeWordDetector {
            triggered = true
        }
        assertTrue(detector.isEnabled())
        detector.setEnabled(false)
        assertEquals(false, detector.isEnabled())
    }

    @Test
    fun `tool engine handles unknown tool gracefully`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = ToolExecutionEngine(context)
        val result = engine.execute("unknownFunction", buildJsonObject {})
        assertTrue(result.contains("उपलब्ध नहीं") || result.contains("कमांड") || result.contains("Unknown command") || result.contains("Error"))
    }
}
