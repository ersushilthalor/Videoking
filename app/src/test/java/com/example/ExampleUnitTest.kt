package com.example

import com.example.gl.LutGenerator
import com.example.gl.ProfileParams
import com.example.gl.VideoProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun testDefaultiPhoneProfileParameters() {
        val params = ProfileParams.createDefault(VideoProfile.IPHONE_NATURAL)
        assertEquals(VideoProfile.IPHONE_NATURAL, params.profile)
        // iPhone profile has controlled low contrast and natural exposure
        assertEquals(1.05f, params.contrast, 0.01f)
        assertTrue("Shadow lift should be positive for rich shadow details", params.shadowLift > 0.3f)
        assertTrue("Skin protection should be high on iPhone profile", params.skinProtection >= 0.9f)
    }

    @Test
    fun testSamsungProfileParameters() {
        val params = ProfileParams.createDefault(VideoProfile.SAMSUNG)
        assertEquals(VideoProfile.SAMSUNG, params.profile)
        // Samsung profile has higher contrast and punchy vibrance
        assertTrue("Samsung should have punchier contrast", params.contrast > 1.20f)
        assertTrue("Samsung should have high vibrance", params.vibrance >= 1.25f)
        assertTrue("Samsung boosts blues and greens", params.colorGains[1] > 1.1f && params.colorGains[2] > 1.1f)
    }

    @Test
    fun testLutGeneratorBuffer() {
        val buffer = LutGenerator.generateLutBuffer(VideoProfile.IPHONE_NATURAL)
        assertNotNull(buffer)
        val expectedSize = LutGenerator.LUT_SIZE * LutGenerator.LUT_SIZE * LutGenerator.LUT_SIZE * 4
        assertEquals(expectedSize, buffer.capacity())
    }
}
