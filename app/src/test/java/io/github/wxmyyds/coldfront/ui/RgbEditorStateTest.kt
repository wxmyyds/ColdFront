package io.github.wxmyyds.coldfront.ui

import io.github.wxmyyds.coldfront.domain.CoolerLiveState
import io.github.wxmyyds.coldfront.domain.ConnectionState
import io.github.wxmyyds.coldfront.domain.LightEffect
import io.github.wxmyyds.coldfront.domain.RGBConfig
import io.github.wxmyyds.coldfront.domain.RgbWriteState
import io.github.wxmyyds.coldfront.domain.RgbWriteStatus
import io.github.wxmyyds.coldfront.ble.GattOperationQueue
import io.github.wxmyyds.coldfront.ble.executeFreshCommand
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
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
        revision: Long = 0L,
    ): CoolerLiveState = CoolerLiveState(
        connection = if (connected) ConnectionState.CONNECTED else ConnectionState.DISCONNECTED,
        deviceAddress = addr,
        connectionSessionId = sessionId,
        rgb = rgb,
        rgbRevision = revision,
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
        assertNull(submitted.submit(request, explicitApply = false).explicitApplyRequestId)
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
        val released = draft.receive(device(rgb = draftConfig, revision = 1L), sent)
        assertEquals(draftConfig, released.config)
        assertFalse(released.dirty)
    }

    @Test
    fun `matching send follows current device when intermediate matching readback was skipped`() {
        val draftConfig = RGBConfig(LightEffect.ALWAYS_BRIGHT, 10, 20, 30)
        val request = RgbWriteState(1, draftConfig, RgbWriteStatus.WRITING)
        val draft = RgbEditorState.fromDevice(device()).submit(request, explicitApply = true)
        val latestConfig = RGBConfig(LightEffect.OFF, 0, 0, 0)

        // No receive(device(draftConfig), SENT) call: lifecycle/StateFlow skipped that emission.
        val released = draft.receive(device(rgb = latestConfig, revision = 2L), request.copy(status = RgbWriteStatus.SENT))
        assertFalse(released.dirty)
        assertEquals(latestConfig, released.config)
        assertNull(released.submittedRequestId)
        assertNull(released.explicitApplyRequestId)
        assertEquals(device().rgb, released.receive(device(), request.copy(status = RgbWriteStatus.SENT)).config)
    }

    @Test
    fun `sent draft survives failed readback until a new device report actually arrives`() {
        val oldDevice = device(revision = 5L)
        val config = RGBConfig(LightEffect.ALWAYS_BRIGHT, 10, 20, 30)
        val sent = RgbWriteState(1, config, RgbWriteStatus.SENT, readbackRevisionAtWrite = 5L)
        val draft = RgbEditorState.fromDevice(oldDevice).submit(sent, explicitApply = true)
        val stillDraft = draft.receive(oldDevice, sent)
        assertEquals(draft, stillDraft)
        assertEquals(config, stillDraft.config)
        assertEquals(RgbWriteStatus.SENT, stillDraft.currentWrite(sent)?.status)
        // Identical old colour is now a real post-write report, so it may take ownership.
        val reported = stillDraft.receive(oldDevice.copy(rgbRevision = 6L), sent)
        assertFalse(reported.dirty)
        assertEquals(oldDevice.rgb, reported.config)
        assertNull(reported.submittedRequestId)
    }

    @Test
    fun `prewrite telemetry cannot release draft even when it arrived after submission`() {
        val initial = device(revision = 1L)
        val config = RGBConfig(LightEffect.ALWAYS_BRIGHT, 10, 20, 30)
        val writing = RgbWriteState(1, config, RgbWriteStatus.WRITING)
        val draft = RgbEditorState.fromDevice(initial).submit(writing, explicitApply = true)
        val queuedTelemetry = initial.copy(rgbRevision = 2L)
        // BLE records revision at write dispatch, not at submit time.
        val sent = writing.copy(status = RgbWriteStatus.SENT, readbackRevisionAtWrite = 2L)
        assertEquals(draft, draft.receive(queuedTelemetry, sent))
        val confirmed = draft.receive(queuedTelemetry.copy(rgb = config, rgbRevision = 3L), sent)
        assertFalse(confirmed.dirty)
        assertEquals(RgbWriteStatus.SENT, confirmed.currentWrite(sent)?.status)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `notification before write coroutine resumes releases draft even if readback fails`() = runTest {
        val old = device(revision = 1L)
        val config = RGBConfig(LightEffect.ALWAYS_BRIGHT, 10, 20, 30)
        var write = RgbWriteState(1, config, RgbWriteStatus.WRITING)
        val draft = RgbEditorState.fromDevice(old).submit(write, explicitApply = true)
        val queue = GattOperationQueue(onTimeout = { error("Unexpected timeout") })
        val owner = Any()
        val target = Any()
        val result = async {
            executeFreshCommand(
                compositeFresh = { true },
                write = { fresh ->
                    queue.execute(owner, target, GattOperationQueue.Kind.WRITE, 100, fresh) {
                        // Same dispatch boundary as CoolerBleManager.enqueueWrite.beforeWrite.
                        write = write.copy(readbackRevisionAtWrite = old.rgbRevision)
                        true
                    }.success
                },
                readBack = { /* Failed read: no additional configuration report. */ },
            )
        }
        runCurrent()
        assertTrue(queue.complete(owner, target, GattOperationQueue.Kind.WRITE, GattOperationQueue.Result(true)))
        // Notification arrives after delivery, before the suspended command consumes its result.
        // Model the event ordering directly, without depending on an artificial GATT sleep.
        assertFalse(result.isCompleted)
        val notified = old.copy(rgb = config, rgbRevision = 2L)
        advanceUntilIdle()
        write = write.completed(write.requestId, result.await())
        val released = draft.receive(notified, write)
        assertFalse(released.dirty)
        assertEquals(config, released.config)
        assertEquals(RgbWriteStatus.SENT, released.currentWrite(write)?.status)
    }

    @Test
    fun `matching completion never replaces an unsent or newer draft`() {
        val base = RgbEditorState.fromDevice(device())
        val request = RgbWriteState(1, base.config.copy(red = 12), RgbWriteStatus.WRITING)
        val submitted = base.submit(request, explicitApply = true)
        val sent = request.copy(status = RgbWriteStatus.SENT)
        val unsent = submitted.edit(request.config.copy(green = 34))
        assertEquals(unsent, unsent.receive(device(rgb = request.config), sent))
        // Even an identical newer draft/request must not be cleared by the older request's send.
        val sameConfigDraft = submitted.edit(request.config)
        assertEquals(sameConfigDraft, sameConfigDraft.receive(device(rgb = request.config), sent))
        val newer = submitted.submit(request.copy(requestId = 2), explicitApply = false)
        assertEquals(newer, newer.receive(device(rgb = request.config), sent))
    }

    @Test
    fun `failed draft remains protected even if readback matches`() {
        val base = RgbEditorState.fromDevice(device())
        val request = RgbWriteState(1, base.config.copy(blue = 45), RgbWriteStatus.FAILED)
        val draft = base.submit(request, explicitApply = true)
        assertEquals(draft, draft.receive(device(), request))
        assertEquals(draft, draft.receive(device(rgb = request.config), request))
        assertEquals(RgbWriteStatus.FAILED, draft.currentWrite(request)?.status)
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
}
