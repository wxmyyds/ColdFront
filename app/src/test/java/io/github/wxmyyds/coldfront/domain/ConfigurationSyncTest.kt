package io.github.wxmyyds.coldfront.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class ConfigurationSyncTest {
    private val cooling = CoolerBleConstants.COOLING_SWITCH_UUID
    private val fan = CoolerBleConstants.FAN_SPEED_CHARACTERISTIC_UUID
    private val rgb = CoolerBleConstants.LIGHT_CONTROL_UUID
    private val auto = CoolerBleConstants.AUTO_MODE_CONTROL_UUID
    private val boost = CoolerBleConstants.BOOST_CONTROL_UUID
    private val protection = CoolerBleConstants.PROTECTION_UUID

    @Test
    fun `initialization waits for every discovered readable config value`() {
        val required = setOf(cooling, fan, rgb, auto, boost, protection)
        val firstConnectionReports = setOf(cooling, fan, rgb, auto, boost)
        assertFalse(configurationReadComplete(required, firstConnectionReports))
        assertTrue(configurationReadComplete(required, firstConnectionReports + protection))
    }

    @Test
    fun `unreadable characteristics are excluded from required configuration`() {
        val readable = setOf(cooling, fan, auto)
        val required = setOf(cooling, fan, auto).intersect(readable)
        assertTrue(configurationReadComplete(required, setOf(cooling, fan, auto)))
        assertTrue(configurationReadComplete(emptySet(), emptySet()))
    }

    @Test
    fun `notifications and reads share one authority set without accepting unrelated UUID`() {
        val required = setOf(cooling, fan)
        val notifications = setOf(cooling)
        val unrelated = UUID(0L, 0L)
        assertFalse(configurationReadComplete(required, notifications + unrelated))
        assertTrue(configurationReadComplete(required, notifications + fan))
    }
}
