package io.github.wxmyyds.coldfront.ui

import androidx.compose.runtime.saveable.SaverScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerLimitNoticeStateTest {
    private val sessionId = 1L
    private val limit = 5

    @Test
    fun `acknowledged limit survives save and restore in the same session`() {
        val acknowledged = PowerLimitNoticeState(sessionId, acknowledgedLimit = limit)
        val restored = restoredState(acknowledged).normalized(sessionId, powerLimited = true, limit = limit)

        assertEquals(acknowledged, restored)
        assertFalse(restored.shouldShow(powerLimited = true, limit = limit))
    }

    @Test
    fun `unacknowledged notice remains visible after save and restore`() {
        val restored = restoredState(PowerLimitNoticeState(sessionId))
            .normalized(sessionId, powerLimited = true, limit = limit)

        assertNull(restored.acknowledgedLimit)
        assertTrue(restored.shouldShow(powerLimited = true, limit = limit))
    }

    @Test
    fun `repeated reports of an acknowledged limit do not show again`() {
        var notice = PowerLimitNoticeState(sessionId, acknowledgedLimit = limit)
        repeat(3) {
            notice = notice.normalized(sessionId, powerLimited = true, limit = limit)
            assertFalse(notice.shouldShow(powerLimited = true, limit = limit))
        }
    }

    @Test
    fun `changed limit requires confirmation and then stays dismissed`() {
        val changedLimit = 4
        var notice = restoredState(PowerLimitNoticeState(sessionId, acknowledgedLimit = limit))
            .normalized(sessionId, powerLimited = true, limit = changedLimit)

        assertNull(notice.acknowledgedLimit)
        assertTrue(notice.shouldShow(powerLimited = true, limit = changedLimit))
        notice = restoredState(notice.copy(acknowledgedLimit = changedLimit))
            .normalized(sessionId, powerLimited = true, limit = changedLimit)
        assertFalse(notice.shouldShow(powerLimited = true, limit = changedLimit))
    }

    @Test
    fun `changing away and back without confirmation still shows the notice`() {
        var notice = PowerLimitNoticeState(sessionId, acknowledgedLimit = limit)
            .normalized(sessionId, powerLimited = true, limit = 4)
        notice = restoredState(notice).normalized(sessionId, powerLimited = true, limit = limit)

        assertTrue(notice.shouldShow(powerLimited = true, limit = limit))
    }

    @Test
    fun `lifting the limit clears confirmation and limiting again shows the notice`() {
        var notice = PowerLimitNoticeState(sessionId, acknowledgedLimit = limit)
            .normalized(sessionId, powerLimited = false, limit = 8)

        assertNull(notice.acknowledgedLimit)
        assertFalse(notice.shouldShow(powerLimited = false, limit = 8))
        notice = restoredState(notice).normalized(sessionId, powerLimited = true, limit = limit)
        assertTrue(notice.shouldShow(powerLimited = true, limit = limit))
    }

    @Test
    fun `restoring into an unrestricted state clears even an unchanged limit`() {
        val notice = restoredState(PowerLimitNoticeState(sessionId, acknowledgedLimit = limit))
            .normalized(sessionId, powerLimited = false, limit = limit)

        assertNull(notice.acknowledgedLimit)
        assertFalse(notice.shouldShow(powerLimited = false, limit = limit))
        assertTrue(notice.normalized(sessionId, powerLimited = true, limit = limit)
            .shouldShow(powerLimited = true, limit = limit))
    }

    @Test
    fun `new session requires confirmation even when restored limit matches`() {
        val newSessionId = 2L
        val notice = restoredState(PowerLimitNoticeState(sessionId, acknowledgedLimit = limit))
            .normalized(newSessionId, powerLimited = true, limit = limit)

        assertEquals(newSessionId, notice.sessionId)
        assertNull(notice.acknowledgedLimit)
        assertTrue(notice.shouldShow(powerLimited = true, limit = limit))
    }

    @Test
    fun `unknown limit cannot show and clears the old acknowledgement`() {
        val notice = restoredState(PowerLimitNoticeState(sessionId, acknowledgedLimit = limit))
            .normalized(sessionId, powerLimited = false, limit = null)

        assertNull(notice.acknowledgedLimit)
        assertFalse(notice.shouldShow(powerLimited = false, limit = null))
        assertFalse(notice.shouldShow(powerLimited = true, limit = null))
        assertTrue(notice.normalized(sessionId, powerLimited = true, limit = limit)
            .shouldShow(powerLimited = true, limit = limit))
    }

    @Test
    fun `saver preserves long session ids and nullable acknowledgements`() {
        for (acknowledgedLimit in listOf(null, 0, limit)) {
            val notice = PowerLimitNoticeState(Long.MAX_VALUE, acknowledgedLimit)
            assertEquals(notice, restoredState(notice))
        }
    }

    private fun restoredState(state: PowerLimitNoticeState): PowerLimitNoticeState {
        val scope = object : SaverScope {
            override fun canBeSaved(value: Any): Boolean = value is Long || value is Int
        }
        val saved = with(PowerLimitNoticeStateSaver) { scope.save(state) }
        assertEquals(listOf(state.sessionId, state.acknowledgedLimit), saved)
        return requireNotNull(PowerLimitNoticeStateSaver.restore(requireNotNull(saved)))
    }
}
