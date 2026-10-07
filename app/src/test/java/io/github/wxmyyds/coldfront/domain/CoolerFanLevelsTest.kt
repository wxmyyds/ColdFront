package io.github.wxmyyds.coldfront.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoolerFanLevelsTest {
    private val type = CoolerDeviceType.JACKET_8_PRO
    private val ranges = listOf(40..42, 43..48, 49..54, 55..60, 61..66, 67..70, 71..74, 75..80)

    @Test
    fun `every legal raw value matches the documented nonuniform gear boundaries`() {
        ranges.forEachIndexed { index, range ->
            for (raw in range) assertEquals("raw=$raw", index + 1, CoolerBleConstants.rawToGear8Pro(raw))
        }
        assertEquals(1, CoolerBleConstants.rawToGear8Pro(Int.MIN_VALUE))
        assertEquals(8, CoolerBleConstants.rawToGear8Pro(Int.MAX_VALUE))
    }

    @Test
    fun `gear commands round trip through integer percentage and reach both endpoints`() {
        for (gear in 1..8) {
            val raw = CoolerBleConstants.gearToRaw8Pro(gear)
            val percent = CoolerBleConstants.gearToPercentage8Pro(gear)
            assertEquals(raw, CoolerBleConstants.percentageToRaw(percent, type))
            assertEquals(gear, CoolerBleConstants.rawToGear8Pro(raw))
        }
        assertEquals(40, CoolerBleConstants.gearToRaw8Pro(1))
        assertEquals(80, CoolerBleConstants.gearToRaw8Pro(8))
        assertEquals(40, CoolerBleConstants.gearToRaw8Pro(0))
        assertEquals(80, CoolerBleConstants.gearToRaw8Pro(9))
    }

    @Test
    fun `device readback preserves the original byte for every gear boundary`() {
        val state = CoolerLiveState(deviceType = type)
        ranges.forEachIndexed { index, range ->
            for (raw in range) {
                val received = requireNotNull(CoolerTelemetryReducer.reduce(
                    state, CoolerBleConstants.FAN_SPEED_CHARACTERISTIC_UUID, byteArrayOf(raw.toByte()),
                )).state
                assertEquals(raw, received.fanRaw)
                assertEquals("raw=$raw", index + 1, received.fanGear)
            }
        }
    }

    @Test
    fun `pending slider target wins over old readback until acknowledged`() {
        val state = CoolerLiveState(deviceType = type, fanRaw = 43, fanPercent = 7)
        assertEquals(2, state.fanGear)
        for (gear in 1..8) {
            assertEquals(gear, state.copy(pendingFanPercent = CoolerBleConstants.gearToPercentage8Pro(gear)).fanGear)
        }
        assertEquals(2, state.copy(pendingFanPercent = null).fanGear)
        assertNull(state.copy(deviceType = CoolerDeviceType.JACKET_5).fanGear)
    }

    @Test
    fun `power limit level maps to the official level to raw table`() {
        // 官方 Jacket8ProManagerV2$a.e(I) 的 packed-switch。
        listOf(40, 46, 52, 58, 64, 68, 72, 76, 80).forEachIndexed { index, raw ->
            assertEquals("level=$index", raw, CoolerBleConstants.fanLimitToRaw8Pro(index))
        }
        // 越界值走官方 packed-switch 的默认分支(0x50)。
        assertEquals(80, CoolerBleConstants.fanLimitToRaw8Pro(-1))
        assertEquals(80, CoolerBleConstants.fanLimitToRaw8Pro(9))
    }

    @Test
    fun `power limit caps the ui gear at the highest gear inside the limit raw`() {
        // 官方 index 7 = raw 76 在本应用 UI 上没有对应档位,所以上限是档位 7(raw 72)。
        assertEquals(7, CoolerBleConstants.maxGearForFanLimit8Pro(7))
        assertEquals(7, CoolerBleConstants.maxGearForFanLimit8Pro(6))
        assertEquals(6, CoolerBleConstants.maxGearForFanLimit8Pro(5))
        assertEquals(1, CoolerBleConstants.maxGearForFanLimit8Pro(0))
        assertEquals(8, CoolerBleConstants.maxGearForFanLimit8Pro(8))
        for (limit in 0..8) {
            val cap = CoolerBleConstants.maxGearForFanLimit8Pro(limit)
            val maxRaw = CoolerBleConstants.fanLimitToRaw8Pro(limit)
            for (gear in 1..8) {
                val raw = CoolerBleConstants.gearToRaw8Pro(gear)
                if (gear <= cap) assertTrue("limit=$limit gear=$gear", raw <= maxRaw)
                else assertTrue("limit=$limit gear=$gear", raw > maxRaw)
            }
        }
    }

    @Test
    fun `clamping a fan request to the power limit never exceeds the limit raw`() {
        for (limit in 0..8) {
            val maxRaw = CoolerBleConstants.fanLimitToRaw8Pro(limit)
            val maxPercent = CoolerBleConstants.rawToPercentage(maxRaw, type)
            for (percent in 0..100) {
                val raw = CoolerBleConstants.percentageToRaw(minOf(percent, maxPercent), type)
                assertTrue("limit=$limit percent=$percent", raw <= maxRaw)
            }
        }
    }

    @Test
    fun `limited state clamps the displayed gear to the cap`() {
        val state = CoolerLiveState(deviceType = type, fanRaw = 80)
        assertEquals(8, state.fanGear)
        val limited = state.copy(fanLimit = 6)
        assertTrue(limited.powerLimited)
        assertEquals(72, limited.fanRawLimit)
        assertEquals(7, limited.fanGearLimit)
        assertEquals(7, limited.fanGear)
        // 限档 8 = 不限档,回到设备上报的档位。
        assertFalse(state.copy(fanLimit = 8).powerLimited)
        assertNull(state.copy(fanLimit = 8).fanGearLimit)
        assertEquals(8, state.copy(fanLimit = 8).fanGear)
        // 该限档语义只属于 8 Pro。
        assertFalse(state.copy(deviceType = CoolerDeviceType.JACKET_6, fanLimit = 6).powerLimited)
    }
}
