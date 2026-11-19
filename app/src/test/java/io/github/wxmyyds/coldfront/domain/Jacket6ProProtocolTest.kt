package io.github.wxmyyds.coldfront.domain

import java.util.UUID
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Jacket6ProProtocolTest {
    private val state = CoolerLiveState(
        deviceType = CoolerDeviceType.JACKET_6_PRO,
        connection = ConnectionState.CONNECTED,
    )

    @Test
    fun `advertisement requires official type and family service`() {
        val services = setOf(CoolerBleConstants.ADVERTISING_SERVICE_UUID)
        assertTrue(Jacket6ProProtocol.isAdvertisement(5, 6, services))
        assertTrue(Jacket6ProProtocol.isAdvertisement(5, 5, services))
        assertFalse(Jacket6ProProtocol.isAdvertisement(5, 6, emptySet()))
        assertFalse(Jacket6ProProtocol.isAdvertisement(4, 6, services))
    }

    @Test
    fun `pro name disambiguates the shared six generation msd`() {
        assertEquals(
            CoolerDeviceType.JACKET_6_PRO,
            CoolerDeviceType.fromMsdType(5, 6, "RM Magcooler 6 Pro"),
        )
        assertEquals(CoolerDeviceType.JACKET_6, CoolerDeviceType.fromMsdType(5, 6, "RM Magcooler 6"))
    }

    @Test
    fun `six pro does not inherit eight pro only telemetry and protection registers`() {
        assertTrue(Jacket6ProProtocol.UNSUPPORTED_CHARACTERISTICS.contains(CoolerBleConstants.RPM_UUID))
        assertTrue(Jacket6ProProtocol.UNSUPPORTED_CHARACTERISTICS.contains(CoolerBleConstants.POWER_UUID))
        assertTrue(Jacket6ProProtocol.UNSUPPORTED_CHARACTERISTICS.contains(CoolerBleConstants.PROTECTION_UUID))
        assertFalse(Jacket6ProProtocol.UNSUPPORTED_CHARACTERISTICS.contains(Jacket6ProProtocol.AUTO_UUID))
        assertFalse(Jacket6ProProtocol.UNSUPPORTED_CHARACTERISTICS.contains(Jacket6ProProtocol.OVER_CLOCKING_UUID))
    }

    @Test
    fun `six pro commands are the official one byte writes`() {
        assertArrayEquals(byteArrayOf(1), Jacket6ProProtocol.hallCommand(HallMode.AUTO))
        assertArrayEquals(byteArrayOf(2), Jacket6ProProtocol.hallCommand(HallMode.ON))
        assertArrayEquals(byteArrayOf(3), Jacket6ProProtocol.hallCommand(HallMode.OFF))
        assertArrayEquals(byteArrayOf(0x28), Jacket6ProProtocol.fanCommand(0))
        assertArrayEquals(byteArrayOf(0x50), Jacket6ProProtocol.fanCommand(200))
        assertArrayEquals(byteArrayOf(1), Jacket6ProProtocol.autoCommand(true))
        assertArrayEquals(byteArrayOf(0), Jacket6ProProtocol.overClockingCommand(false))
    }

    @Test
    fun `six pro status envelope is not interpreted as the eight pro tag table`() {
        val hall = requireNotNull(
            Jacket6ProProtocol.statusUpdate(state, byteArrayOf(1, 1)),
        ).state
        assertEquals(HallMode.AUTO, hall.hallMode)
        assertTrue(hall.coolingOn)

        val fan = requireNotNull(
            Jacket6ProProtocol.statusUpdate(state, byteArrayOf(2, 0x50)),
        ).state
        assertEquals(80, fan.fanRaw)
        assertEquals(100, fan.fanPercent)

        assertEquals(
            true,
            Jacket6ProProtocol.statusUpdate(state, byteArrayOf(5, 0x80.toByte()))?.state?.outputHasLoad,
        )
        assertEquals(
            true,
            Jacket6ProProtocol.statusUpdate(state, byteArrayOf(5, 0x81.toByte()))?.state?.outputHasLoad,
        )
        assertEquals(
            false,
            Jacket6ProProtocol.statusUpdate(state, byteArrayOf(5, 0))?.state?.outputHasLoad,
        )
        assertEquals(
            false,
            Jacket6ProProtocol.statusUpdate(state, byteArrayOf(5, 1))?.state?.outputHasLoad,
        )
        assertNull(Jacket6ProProtocol.statusUpdate(state, byteArrayOf(5, 2)))
        assertNull(
            CoolerTelemetryReducer.reduce(
                state,
                UUID.fromString("00001015-0000-1000-8000-00805f9b34fb"),
                byteArrayOf(7, 6),
            ),
        )
    }

    @Test
    fun `six pro temperature does not apply eight pro offset`() {
        val update = requireNotNull(
            CoolerTelemetryReducer.reduce(
                state,
                CoolerBleConstants.STATUS_UUID,
                byteArrayOf(4, 20),
            ),
        )
        assertEquals(20f, update.state.temperatureC)

        val standalone = requireNotNull(
            CoolerTelemetryReducer.reduce(
                state,
                CoolerBleConstants.TEMPERATURE_NOTIFICATION_UUID,
                byteArrayOf(0x01, 0x25),
            ),
        )
        assertEquals(1f, standalone.state.temperatureC)
        assertNull(
            CoolerTelemetryReducer.reduce(
                state,
                CoolerBleConstants.TEMPERATURE_NOTIFICATION_UUID,
                byteArrayOf(0x80.toByte(), 20),
            ),
        )
    }

    @Test
    fun `six pro plus shares the six pro protocol path`() {
        val proPlusState = state.copy(deviceType = CoolerDeviceType.JACKET_6_PRO_PLUS)
        // 与 6 Pro 同族:温度无 8 Pro 校准、风扇 raw 40–80、Hall 三态、状态包 kinds 01–05。
        assertEquals(
            25f,
            requireNotNull(
                CoolerTelemetryReducer.reduce(proPlusState, CoolerBleConstants.STATUS_UUID, byteArrayOf(4, 25)),
            ).state.temperatureC,
        )
        val fan = requireNotNull(
            CoolerTelemetryReducer.reduce(
                proPlusState,
                CoolerBleConstants.FAN_SPEED_CHARACTERISTIC_UUID,
                byteArrayOf(0x4c.toByte()),
            ),
        ).state
        assertEquals(76, fan.fanRaw)
        assertEquals(90, fan.fanPercent)
        val hall = requireNotNull(
            CoolerTelemetryReducer.reduce(proPlusState, CoolerBleConstants.COOLING_SWITCH_UUID, byteArrayOf(1)),
        ).state
        assertEquals(HallMode.AUTO, hall.hallMode)
        assertTrue(hall.coolingOn)
    }

    @Test
    fun `six pro plus output has load treats 0x7f as the official no-op`() {
        val proPlusState = state.copy(deviceType = CoolerDeviceType.JACKET_6_PRO_PLUS)
        // 官方 onOutputHasLoad 识别 0x7f 但协程为空实现:不变更负载状态,也不当作未知。
        assertNull(Jacket6ProProtocol.statusUpdate(proPlusState, byteArrayOf(5, 0x7f)))
        assertEquals(
            true,
            Jacket6ProProtocol.statusUpdate(proPlusState, byteArrayOf(5, 0x80.toByte()))?.state?.outputHasLoad,
        )
        assertEquals(
            false,
            Jacket6ProProtocol.statusUpdate(proPlusState, byteArrayOf(5, 1))?.state?.outputHasLoad,
        )
    }
}
