package io.github.wxmyyds.coldfront.domain

import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
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

    @Test
    fun `every model carries a display name and non-empty aliases`() {
        // 展示名 + 别名两层覆盖全部设备;别名为官方广播名/历史展示名。
        CoolerDeviceType.entries.forEach { type ->
            assertTrue("type=$type", type.deviceName.isNotBlank())
            assertTrue("type=$type", type.aliases.isNotEmpty())
        }
        // 官方广播名作为别名进入枚举(节选)。
        assertTrue(CoolerDeviceType.JACKET_1.aliases.contains("RedMagic Ice Dock"))
        assertTrue(CoolerDeviceType.JACKET_3.aliases.contains("红魔磁吸背夹"))
        assertTrue(CoolerDeviceType.JACKET_4.aliases.contains("RM Magcooler 4pro"))
        assertTrue(CoolerDeviceType.JACKET_6.aliases.contains("红魔散热器6"))
        assertTrue(CoolerDeviceType.JACKET_6_PRO.aliases.containsAll(
            listOf("Cryo Cooler 6 Pro", "红魔散热器6pro", "RM Magcooler 6pro", "PA1246"),
        ))
        assertTrue(CoolerDeviceType.JACKET_6_PRO_PLUS.aliases.contains("Cryo Cooler 6 Pro+"))
        assertTrue(CoolerDeviceType.JACKET_8_PRO.aliases.contains("RM Magcooler 8pro"))
        // Pro 系列展示名统一为 REDMAGIC,旧称 Cryo 为别名。
        assertEquals("REDMAGIC Cooler 6 Pro", CoolerDeviceType.JACKET_6_PRO.deviceName)
        assertEquals("REDMAGIC Cooler 6 Pro+", CoolerDeviceType.JACKET_6_PRO_PLUS.deviceName)
        assertEquals("REDMAGIC Cooler 8 Pro", CoolerDeviceType.JACKET_8_PRO.deviceName)
        // 旧称别名仍可按广播名兜底识别。
        assertEquals(CoolerDeviceType.JACKET_6_PRO, CoolerDeviceType.fromBleName("Cryo Cooler 6 Pro"))
        assertEquals(CoolerDeviceType.JACKET_6_PRO_PLUS, CoolerDeviceType.fromBleName("Cryo Cooler 6 Pro+"))
        assertEquals(CoolerDeviceType.JACKET_8_PRO, CoolerDeviceType.fromBleName("Cryo Cooler 8 Pro"))
    }

    @Test
    fun `official pro plus broadcast name identifies the pro plus type`() {
        // 官方 DeviceHelperKt.X:包含 "REDMAGIC Cooler 6 pro+"(忽略大小写)。
        assertEquals(
            CoolerDeviceType.JACKET_6_PRO_PLUS,
            CoolerDeviceType.fromBleName("REDMAGIC Cooler 6 pro+"),
        )
        assertEquals(
            CoolerDeviceType.JACKET_6_PRO_PLUS,
            CoolerDeviceType.fromBleName("redmagic cooler 6 PRO+"),
        )
        // 与 6/6 Pro 同 MSD (0x05,0x06)/(0x05,0x05),仅名称可区分。
        assertEquals(
            CoolerDeviceType.JACKET_6_PRO_PLUS,
            CoolerDeviceType.fromMsdType(5, 6, "REDMAGIC Cooler 6 pro+"),
        )
        assertEquals(
            CoolerDeviceType.JACKET_6_PRO_PLUS,
            CoolerDeviceType.fromMsdType(5, 5, "RM Magcooler 6 Pro Plus"),
        )
        assertTrue(CoolerDeviceType.JACKET_6_PRO_PLUS.matchesBleName("REDMAGIC Cooler 6 pro+"))
        // 6 Pro 仍拒绝 Plus 名称,不得误判为 6 Pro。
        assertFalse(CoolerDeviceType.JACKET_6_PRO.matchesBleName("REDMAGIC Cooler 6 pro+"))
        // Pro+ 名称兜底仍要求红魔系前缀。
        assertNull(CoolerDeviceType.fromBleName("Unrelated 6 Pro Plus"))
        // 8 Pro 的 MSD 精确匹配不受名称影响。
        assertEquals(
            CoolerDeviceType.JACKET_8_PRO,
            CoolerDeviceType.fromMsdType(5, 8, "REDMAGIC Cooler 6 pro+"),
        )
    }
}
