package io.github.wxmyyds.coldfront.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoolerTelemetryParserTest {
    @Test
    fun `fan raw bytes above signed range reach full speed on older models`() {
        assertEquals(100, CoolerTelemetryParser.fanPercent(byteArrayOf(200.toByte()), CoolerDeviceType.JACKET_6))
        assertEquals(55, CoolerTelemetryParser.fanPercent(byteArrayOf(128.toByte()), CoolerDeviceType.JACKET_6))
        assertEquals(100, CoolerTelemetryParser.fanPercent(byteArrayOf(80), CoolerDeviceType.JACKET_8_PRO))
        assertEquals(0, CoolerTelemetryParser.fanPercent(byteArrayOf(40), CoolerDeviceType.JACKET_8_PRO))
        assertNull(CoolerTelemetryParser.fanPercent(byteArrayOf(), CoolerDeviceType.JACKET_8_PRO))
    }

    @Test
    fun `standalone power is unsigned and empty packets are ignored`() {
        assertEquals(200, CoolerTelemetryParser.unsignedByte(byteArrayOf(200.toByte())))
        assertEquals(255, CoolerTelemetryParser.unsignedByte(byteArrayOf(0xFF.toByte())))
        assertNull(CoolerTelemetryParser.unsignedByte(byteArrayOf()))
    }

    @Test
    fun `rpm is unsigned big endian`() {
        assertEquals(6200, CoolerTelemetryParser.bigEndianShort(byteArrayOf(0x18, 0x38)))
        assertEquals(65535, CoolerTelemetryParser.bigEndianShort(byteArrayOf(-1, -1)))
        assertNull(CoolerTelemetryParser.bigEndianShort(byteArrayOf(1)))
    }

    @Test
    fun `temperature preserves existing six degree calibration and formats`() {
        assertEquals(14f, CoolerTelemetryParser.temperature(byteArrayOf(20)))
        assertEquals(-16f, CoolerTelemetryParser.temperature(byteArrayOf(-10)))
        assertEquals(14f, CoolerTelemetryParser.temperature(byteArrayOf(0x04, 20)))
        assertEquals(14f, CoolerTelemetryParser.temperature(byteArrayOf(0x01, 0x25))) // 293 K
        assertEquals(34f, CoolerTelemetryParser.temperature(byteArrayOf(0, 40)))
        assertEquals(-16f, CoolerTelemetryParser.temperature(byteArrayOf(-1, -10)))
        assertNull(CoolerTelemetryParser.temperature(byteArrayOf()))
        assertNull(CoolerTelemetryParser.temperature(byteArrayOf(81)))
    }

    @Test
    fun `undocumented tagged and fallback temperature behavior is not reinterpreted`() {
        // Preserve original parser rather than assuming 0x08 strips a tag (hardware unresolved).
        assertEquals(2f, CoolerTelemetryParser.temperature(byteArrayOf(0x08, 0x01, 0x25)))
        assertEquals(14f, CoolerTelemetryParser.temperature(byteArrayOf(20, 100)))
        assertNull(CoolerTelemetryParser.temperature(byteArrayOf(0x04, 100)))
    }
}
