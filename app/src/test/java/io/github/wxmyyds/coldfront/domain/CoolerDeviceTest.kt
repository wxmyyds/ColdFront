package io.github.wxmyyds.coldfront.domain

import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class CoolerDeviceTest {
    private val device = CoolerDevice("AA:BB:CC:DD:EE:FF", "Magcooler", CoolerDeviceType.JACKET_3, -80)

    @Test
    fun `device equality includes advertisement values not just address`() {
        assertEquals(device, device.copy())
        assertEquals(device.hashCode(), device.copy().hashCode())
        assertNotEquals(device, device.copy(rssi = -50))
        assertNotEquals(device, device.copy(bleName = "Magcooler 6"))
        assertNotEquals(device, device.copy(deviceType = CoolerDeviceType.JACKET_6))
        assertNotEquals(device, device.copy(matchedByName = true))
    }

    @Test
    fun `identical scan events do not force state changes via a clock field`() {
        val initial = listOf(device)
        val devices = MutableStateFlow(initial)
        devices.value = listOf(CoolerDevice("AA:BB:CC:DD:EE:FF", "Magcooler", CoolerDeviceType.JACKET_3, -80))
        assertSame(initial, devices.value)
    }

    @Test
    fun `state flow publishes changed advertisement at the same address`() {
        val devices = MutableStateFlow(listOf(device))
        val changed = device.copy(rssi = -45, bleName = "Magcooler 6", deviceType = CoolerDeviceType.JACKET_6)
        devices.value = listOf(changed)
        assertEquals(changed, devices.value.single())
        assertEquals(-45, devices.value.single().rssi)
        assertEquals("Magcooler 6", devices.value.single().displayName)
    }

    @Test
    fun `explicit generations beat generic Magcooler third generation fallback`() {
        assertEquals(CoolerDeviceType.JACKET_4, CoolerDeviceType.fromBleName("Magcooler 4"))
        assertEquals(CoolerDeviceType.JACKET_5, CoolerDeviceType.fromBleName("Magcooler 5 Pro"))
        assertEquals(CoolerDeviceType.JACKET_5_LITE, CoolerDeviceType.fromBleName("Magcooler 5 Lite"))
        assertEquals(CoolerDeviceType.JACKET_6, CoolerDeviceType.fromBleName("Magcooler 6"))
        assertEquals(CoolerDeviceType.JACKET_6_PRO, CoolerDeviceType.fromBleName("Magcooler 6 Pro"))
        assertEquals(CoolerDeviceType.JACKET_8_PRO, CoolerDeviceType.fromBleName("Magcooler 8 Pro"))
        assertEquals(CoolerDeviceType.JACKET_3, CoolerDeviceType.fromBleName("Magcooler"))
        assertEquals(CoolerDeviceType.JACKET_3, CoolerDeviceType.fromBleName("MAGCOOLER 3"))
        assertNull(CoolerDeviceType.fromBleName("Unrelated 6 Pro"))
        assertNull(CoolerDeviceType.fromBleName(null))
        assertNull(CoolerDeviceType.fromBleName(" "))
    }
}
