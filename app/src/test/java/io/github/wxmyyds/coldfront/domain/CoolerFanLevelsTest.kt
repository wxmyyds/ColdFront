package io.github.wxmyyds.coldfront.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoolerFanLevelsTest {
    private val type = CoolerDeviceType.JACKET_8_PRO
    // Jacket8ProManagerV2$a.d(I) 中的 raw 边界对应本应用的 1–9 档。
    private val ranges = listOf(
        40..43, 44..49, 50..55, 56..61, 62..67, 68..71, 72..74, 75..78, 79..80,
    )

    @Test
    fun `every legal raw value matches the documented nonuniform gear boundaries`() {
        ranges.forEachIndexed { index, range ->
            for (raw in range) assertEquals("raw=$raw", index + 1, CoolerBleConstants.rawToGear8Pro(raw))
        }
        assertEquals(1, CoolerBleConstants.rawToGear8Pro(Int.MIN_VALUE))
        assertEquals(9, CoolerBleConstants.rawToGear8Pro(Int.MAX_VALUE))
    }

    @Test
    fun `gear commands round trip through integer percentage and reach both endpoints`() {
        for (gear in 1..9) {
            val raw = CoolerBleConstants.gearToRaw8Pro(gear)
            val percent = CoolerBleConstants.gearToPercentage8Pro(gear)
            assertEquals(raw, CoolerBleConstants.percentageToRaw(percent, type))
            assertEquals(gear, CoolerBleConstants.rawToGear8Pro(raw))
        }
        assertEquals(40, CoolerBleConstants.gearToRaw8Pro(1))
        assertEquals(76, CoolerBleConstants.gearToRaw8Pro(8))
        assertEquals(80, CoolerBleConstants.gearToRaw8Pro(9))
        assertEquals(40, CoolerBleConstants.gearToRaw8Pro(0))
        assertEquals(80, CoolerBleConstants.gearToRaw8Pro(10))
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
        assertEquals(1, state.fanGear)
        for (gear in 1..9) {
            assertEquals(gear, state.copy(pendingFanPercent = CoolerBleConstants.gearToPercentage8Pro(gear)).fanGear)
        }
        assertEquals(1, state.copy(pendingFanPercent = null).fanGear)
        assertNull(state.copy(deviceType = CoolerDeviceType.JACKET_5).fanGear)
    }

    @Test
    fun `power limit level maps to the official level to raw table`() {
        // Jacket8ProManagerV2$a.e(I) 使用 packed-switch 映射限档。
        listOf(40, 46, 52, 58, 64, 68, 72, 76, 80).forEachIndexed { index, raw ->
            assertEquals("level=$index", raw, CoolerBleConstants.fanLimitToRaw8Pro(index))
        }
        // 越界值使用 packed-switch 的默认值 0x50。
        assertEquals(80, CoolerBleConstants.fanLimitToRaw8Pro(-1))
        assertEquals(80, CoolerBleConstants.fanLimitToRaw8Pro(9))
    }

    @Test
    fun `power limit caps the ui gear at the highest gear inside the limit raw`() {
        // UI 档位 1–9 分别对应官方限档索引 0–8。
        assertEquals(8, CoolerBleConstants.maxGearForFanLimit8Pro(7))
        assertEquals(7, CoolerBleConstants.maxGearForFanLimit8Pro(6))
        assertEquals(6, CoolerBleConstants.maxGearForFanLimit8Pro(5))
        assertEquals(1, CoolerBleConstants.maxGearForFanLimit8Pro(0))
        assertEquals(9, CoolerBleConstants.maxGearForFanLimit8Pro(8))
        for (limit in 0..8) {
            val cap = CoolerBleConstants.maxGearForFanLimit8Pro(limit)
            val maxRaw = CoolerBleConstants.fanLimitToRaw8Pro(limit)
            for (gear in 1..9) {
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
        assertEquals(9, state.fanGear)
        val limited = state.copy(fanLimit = 6)
        assertTrue(limited.powerLimited)
        assertEquals(72, limited.fanRawLimit)
        assertEquals(7, limited.fanGearLimit)
        assertEquals(7, limited.fanGear)
        // 限档值 8 表示不限档，此时使用设备上报的档位。
        assertFalse(state.copy(fanLimit = 8).powerLimited)
        assertNull(state.copy(fanLimit = 8).fanGearLimit)
        assertEquals(9, state.copy(fanLimit = 8).fanGear)
        // 只有 8 Pro 使用这套限档语义。
        assertFalse(state.copy(deviceType = CoolerDeviceType.JACKET_6, fanLimit = 6).powerLimited)
    }

    private val proPlus = CoolerDeviceType.JACKET_6_PRO_PLUS

    @Test
    fun `six pro plus official boundaries match the jacket6proplus companion table`() {
        // Jacket6ProPlusManagerV2$a.c(I) 将 raw 边界 [40,43] 映射到档 0，将 [78,80] 映射到档 8；本应用显示为 1–9 档。
        val ranges = listOf(
            40..43, 44..49, 50..55, 56..61, 62..67, 68..71, 72..74, 75..78, 79..80,
        )
        ranges.forEachIndexed { index, range ->
            for (raw in range) {
                assertEquals("raw=$raw", index + 1, CoolerBleConstants.rawToGear6ProPlus(raw))
            }
        }
        assertEquals(1, CoolerBleConstants.rawToGear6ProPlus(0))
        assertEquals(9, CoolerBleConstants.rawToGear6ProPlus(Int.MAX_VALUE))
    }

    @Test
    fun `six pro plus nine gears round trip through percentage and reach endpoints`() {
        // Jacket6ProPlusManagerV2$a.d(I) 将档位索引 0–8 映射到 raw 值 40/46/52/58/64/68/72/76/80。
        val canonical = listOf(40, 46, 52, 58, 64, 68, 72, 76, 80)
        canonical.forEachIndexed { index, raw ->
            val gear = index + 1
            assertEquals("gear=$gear", raw, CoolerBleConstants.gearToRaw6ProPlus(gear))
            val percent = CoolerBleConstants.gearToPercentage6ProPlus(gear)
            assertEquals("gear=$gear", raw, CoolerBleConstants.percentageToRaw(percent, proPlus))
        }
        assertEquals(40, CoolerBleConstants.gearToRaw6ProPlus(0))
        assertEquals(80, CoolerBleConstants.gearToRaw6ProPlus(10))
    }

    @Test
    fun `six pro plus raw writes snap to the official gear grid`() {
        val canonical = listOf(40, 46, 52, 58, 64, 68, 72, 76, 80)
        for (raw in 40..80) {
            val snapped = CoolerBleConstants.snapToGearRaw6ProPlus(raw)
            assertTrue("raw=$raw", snapped in canonical)
            if (raw in canonical) assertEquals("raw=$raw", raw, snapped)
        }
        // Jacket6ProPlusManagerV2$a.c(I) 将 75–78 映射到 raw 76，将 79–80 映射到 raw 80。
        assertEquals(76, CoolerBleConstants.snapToGearRaw6ProPlus(75))
        assertEquals(76, CoolerBleConstants.snapToGearRaw6ProPlus(78))
        assertEquals(80, CoolerBleConstants.snapToGearRaw6ProPlus(79))
    }

    @Test
    fun `six pro plus displays gears from device raw and pending intent`() {
        val state = CoolerLiveState(deviceType = proPlus, fanRaw = 76)
        assertEquals(8, state.fanGear)
        assertEquals(9, state.copy(fanRaw = 80).fanGear)
        assertEquals(
            3,
            state.copy(pendingFanPercent = CoolerBleConstants.gearToPercentage6ProPlus(3)).fanGear,
        )
        // 九档显示只用于 6 Pro+；6 Pro 使用连续 raw 滑条。
        assertNull(state.copy(deviceType = CoolerDeviceType.JACKET_6_PRO).fanGear)
        assertNull(state.copy(deviceType = CoolerDeviceType.JACKET_6).fanGear)
    }
}
