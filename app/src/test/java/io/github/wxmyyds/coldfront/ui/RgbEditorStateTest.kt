package io.github.wxmyyds.coldfront.ui

import io.github.wxmyyds.coldfront.domain.CoolerLiveState
import io.github.wxmyyds.coldfront.domain.ConnectionState
import io.github.wxmyyds.coldfront.domain.LightEffect
import io.github.wxmyyds.coldfront.domain.RGBConfig
import io.github.wxmyyds.coldfront.domain.RgbWriteState
import io.github.wxmyyds.coldfront.domain.RgbWriteStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** RGB 编辑状态机回归测试：修复「点击即显示已同步」与「设备回读被一次性锁死」。 */
class RgbEditorStateTest {

    private val addr = "AA:BB:CC:DD:EE:FF"
    private val sessionId = 7L

    private fun device(
        rgb: RGBConfig? = RGBConfig(LightEffect.BREATH_SINGLE, 255, 0, 0),
        connected: Boolean = true,
    ): CoolerLiveState = CoolerLiveState(
        connection = if (connected) ConnectionState.CONNECTED else ConnectionState.DISCONNECTED,
        deviceAddress = addr,
        connectionSessionId = sessionId,
        rgb = rgb,
    )

    @Test
    fun `submit marks writing and explicit request id`() {
        val base = RgbEditorState.fromDevice(device())
        val request = RgbWriteState(1, base.config, RgbWriteStatus.WRITING)
        val submitted = base.submit(request, explicitApply = true)

        assertTrue(submitted.dirty)
        assertEquals(1L, submitted.submittedRequestId)
        assertEquals(1L, submitted.explicitApplyRequestId)
        assertEquals(RgbWriteStatus.WRITING, submitted.currentWrite(request)?.status)
        assertFalse(submitted.submit(request, explicitApply = false).appliesExplicitly())
    }

    @Test
    fun `edit invalidates previous write status`() {
        val base = RgbEditorState.fromDevice(device())
        val request = RgbWriteState(1, base.config, RgbWriteStatus.SENT)
        val submitted = base.submit(request, explicitApply = true)
        assertTrue(submitted.currentWrite(request)?.status == RgbWriteStatus.SENT)

        val edited = submitted.edit(base.config.copy(red = 0))
        assertNull(edited.currentWrite(request))
    }

    @Test
    fun `stale completion must not overwrite a newer request`() {
        val stale = RgbWriteState(1, RGBConfig(LightEffect.ALWAYS_BRIGHT, 0, 0, 255), RgbWriteStatus.WRITING)
        val fresh = stale.completed(1, success = false)
        assertEquals(RgbWriteStatus.FAILED, fresh.status)

        // 更新的请求(2)已覆盖状态后，旧请求(1)的迟到完成不得改写状态
        val superseded = RgbWriteState(2, RGBConfig(LightEffect.OFF, 0, 0, 0), RgbWriteStatus.WRITING)
        val current = superseded.completed(1, success = true)
        assertEquals(RgbWriteStatus.WRITING, current.status)
        val settled = superseded.completed(2, success = true)
        assertEquals(RgbWriteStatus.SENT, settled.status)
    }

    @Test
    fun `receive follows device readback while not dirty`() {
        val base = RgbEditorState.fromDevice(device())
        val changed = base.receive(device(rgb = RGBConfig(LightEffect.OFF, 0, 0, 0)), null)
        assertEquals(RGBConfig(LightEffect.OFF, 0, 0, 0), changed.config)
        assertFalse(changed.dirty)
    }

    @Test
    fun `dirty draft survives unrelated readback until its own send completes`() {
        val base = RgbEditorState.fromDevice(device())
        val draftConfig = RGBConfig(LightEffect.ALWAYS_BRIGHT, 10, 20, 30)
        val request = RgbWriteState(1, draftConfig, RgbWriteStatus.WRITING)
        val draft = base.submit(request, explicitApply = true)

        // 设备回读仍是旧值：草稿必须保留
        val stillDraft = draft.receive(device(), request)
        assertEquals(draftConfig, stillDraft.config)
        assertTrue(stillDraft.dirty)

        // 写入完成且设备状态已与草稿一致：释放保护，恢复跟随回读
        val sent = request.copy(status = RgbWriteStatus.SENT)
        val released = draft.receive(device(rgb = draftConfig), sent)
        assertEquals(draftConfig, released.config)
        assertFalse(released.dirty)
    }

    @Test
    fun `restored draft never leaks across devices or sessions`() {
        val base = RgbEditorState.fromDevice(device())
        val draft = base.submit(
            RgbWriteState(1, base.config.copy(red = 99), RgbWriteStatus.WRITING),
            explicitApply = false,
        )

        val otherDevice = draft.receive(device().copy(deviceAddress = "11:22:33:44:55:66"), null)
        assertEquals("11:22:33:44:55:66", otherDevice.address)
        assertFalse(otherDevice.dirty)

        val otherSession = draft.receive(device().copy(connectionSessionId = 8L), null)
        assertEquals(8L, otherSession.sessionId)
        assertFalse(otherSession.dirty)
    }

    @Test
    fun `disconnected state resets to defaults`() {
        val base = RgbEditorState.fromDevice(device())
        val draft = base.submit(
            RgbWriteState(1, base.config.copy(green = 123), RgbWriteStatus.WRITING),
            explicitApply = false,
        )
        val reset = draft.receive(device(connected = false, rgb = null), null)
        assertFalse(reset.connected)
        assertEquals(RGBConfig(LightEffect.ALWAYS_BRIGHT, 0, 80, 200), reset.config)
        assertNull(reset.currentWrite(null))
    }

    private fun RgbEditorState.appliesExplicitly(): Boolean =
        currentWrite(null) != null && explicitApplyRequestId == submittedRequestId
}
