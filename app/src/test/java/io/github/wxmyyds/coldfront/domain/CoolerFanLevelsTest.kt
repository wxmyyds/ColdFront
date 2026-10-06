package io.github.wxmyyds.coldfront.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
