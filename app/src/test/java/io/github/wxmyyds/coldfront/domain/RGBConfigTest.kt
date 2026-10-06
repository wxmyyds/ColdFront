package io.github.wxmyyds.coldfront.domain

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 灯效回读解析与命令编码回归测试。 */
class RGBConfigTest {

    @Test
    fun `notification with color bytes updates color`() {
        val parsed = RGBConfig.fromNotification(
            byteArrayOf(0x04, 0x0A.toByte(), 0x14, 0x1E),
            previous = RGBConfig(LightEffect.ALWAYS_BRIGHT, 255, 0, 0),
        )
        assertEquals(RGBConfig(LightEffect.ALWAYS_BRIGHT, 10, 20, 30), parsed)
    }

    @Test
    fun `mode-only notification keeps previous color and effect`() {
        val previous = RGBConfig(LightEffect.ALWAYS_BRIGHT, 255, 0, 0)
        val parsed = RGBConfig.fromNotification(byteArrayOf(0x02), previous)
        assertEquals(RGBConfig(LightEffect.BREATH_FULLCOLOR, 255, 0, 0), parsed)
    }

    @Test
    fun `single-breath notification keeps real variant`() {
        val parsed = RGBConfig.fromNotification(byteArrayOf(0x03, 0, 0x50, 0xC8.toByte()), null)
        assertEquals(RGBConfig(LightEffect.BREATH_SINGLE, 0, 80, 200), parsed)
    }

    @Test
    fun `color-bearing modes reject mode-only replies instead of inventing a default color`() {
        for (code in listOf(LightEffect.ALWAYS_BRIGHT.code, LightEffect.BREATH_SINGLE.code)) {
            assertNull(RGBConfig.fromNotification(byteArrayOf(code), null))
            assertNull(RGBConfig.fromNotification(byteArrayOf(code, 1, 2), null))
        }
    }

    @Test
    fun `color-independent effects can be confirmed without RGB payload bytes`() {
        assertEquals(RGBConfig(LightEffect.COLORFUL), RGBConfig.fromNotification(byteArrayOf(1), null))
        assertEquals(RGBConfig(LightEffect.BREATH_FULLCOLOR), RGBConfig.fromNotification(byteArrayOf(2), null))
        assertEquals(RGBConfig(LightEffect.SCENE), RGBConfig.fromNotification(byteArrayOf(5), null))
        assertEquals(RGBConfig(LightEffect.OFF), RGBConfig.fromNotification(byteArrayOf(6), null))
    }

    @Test
    fun `unknown mode and empty payload are rejected`() {
        assertNull(RGBConfig.fromNotification(byteArrayOf(0x63), null))
        assertNull(RGBConfig.fromNotification(byteArrayOf(), null))
    }

    @Test
    fun `colorless effects zero color bytes in command`() {
        assertArrayEquals(
            byteArrayOf(0x02, 0, 0, 0),
            RGBConfig(LightEffect.BREATH_FULLCOLOR, 9, 9, 9).toCommand(),
        )
        assertArrayEquals(
            byteArrayOf(0x05, 0, 0, 0),
            RGBConfig(LightEffect.SCENE, 9, 9, 9).toCommand(),
        )
        assertArrayEquals(
            byteArrayOf(0x06, 0, 0, 0),
            RGBConfig(LightEffect.OFF, 9, 9, 9).toCommand(),
        )
    }

    @Test
    fun `color effects keep color bytes in command`() {
        assertArrayEquals(
            byteArrayOf(0x03, 0x01, 0x02, 0x03),
            RGBConfig(LightEffect.BREATH_SINGLE, 1, 2, 3).toCommand(),
        )
        assertArrayEquals(
            byteArrayOf(0x04, 0x0A.toByte(), 0x14, 0x1E),
            RGBConfig(LightEffect.ALWAYS_BRIGHT, 10, 20, 30).toCommand(),
        )
    }
}
