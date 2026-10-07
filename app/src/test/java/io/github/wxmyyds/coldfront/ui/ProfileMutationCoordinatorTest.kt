package io.github.wxmyyds.coldfront.ui

import io.github.wxmyyds.coldfront.data.ProfileRepository
import io.github.wxmyyds.coldfront.data.TestPreferencesStore
import io.github.wxmyyds.coldfront.data.activeKey
import io.github.wxmyyds.coldfront.data.defaultKey
import io.github.wxmyyds.coldfront.data.expectFailure
import io.github.wxmyyds.coldfront.data.profilePreferences
import io.github.wxmyyds.coldfront.data.serviceKey
import io.github.wxmyyds.coldfront.domain.ConnectionState
import io.github.wxmyyds.coldfront.domain.CoolerDeviceType
import io.github.wxmyyds.coldfront.domain.CoolerLiveState
import io.github.wxmyyds.coldfront.domain.CoolerProfile
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileMutationCoordinatorTest {
    private val saved = CoolerProfile(
        id = "saved",
        name = "Saved cooler",
        deviceType = CoolerDeviceType.JACKET_8_PRO,
        macAddress = "AA:BB:CC:DD:EE:FF",
        createdAtMs = 10L,
        lastConnectedAtMs = 20L,
    )
    private val connected = CoolerLiveState(
        connection = ConnectionState.CONNECTED,
        deviceType = saved.deviceType,
        deviceAddress = saved.macAddress,
        connectionSessionId = 1L,
    )

    @Test
    fun `record queued before delete commits first and deletion wins`() = runTest {
        val enteredRecord = CompletableDeferred<Unit>()
        val finishRecord = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        var persisted: CoolerProfile? = null
        val coordinator = ProfileMutationCoordinator(
            scope = backgroundScope,
            record = {
                events += "record started"
                enteredRecord.complete(Unit)
                finishRecord.await()
                persisted = saved
                events += "record committed"
            },
            delete = { id ->
                assertEquals(saved.id, id)
                events += "delete committed"
                persisted.also { persisted = null }
            },
        )
        val recording = async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.recordConnection(connected)
        }
        enteredRecord.await()
        val deletion = async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.deleteProfile(saved.id, connected)
        }
        runCurrent()
        assertEquals(listOf("record started"), events)
        assertFalse(deletion.isCompleted)

        finishRecord.complete(Unit)
        recording.await()
        assertSame(saved, deletion.await())
        assertNull(persisted)
        coordinator.recordConnection(connected)
        assertEquals(listOf("record started", "record committed", "delete committed"), events)
        assertNull(persisted)
    }

    @Test
    fun `queued deletion captures a replacement session when it starts and suppresses its record`() = runTest {
        val enteredRecord = CompletableDeferred<Unit>()
        val finishRecord = CompletableDeferred<Unit>()
        val recorded = mutableListOf<Long>()
        var persisted: CoolerProfile? = null
        var currentState = connected
        var snapshotReads = 0
        val coordinator = ProfileMutationCoordinator(
            scope = backgroundScope,
            record = { state ->
                recorded += state.connectionSessionId
                enteredRecord.complete(Unit)
                finishRecord.await()
                persisted = saved
            },
            delete = { persisted.also { persisted = null } },
        )
        val firstRecording = async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.recordConnection(connected)
        }
        enteredRecord.await()
        val deletion = async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.deleteProfile(saved.id) {
                snapshotReads++
                currentState
            }
        }
        val replacement = connected.copy(connectionSessionId = 2L)
        currentState = replacement
        val replacementRecording = async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.recordConnection(replacement)
        }
        runCurrent()
        assertEquals(0, snapshotReads)
        assertFalse(deletion.isCompleted)
        assertFalse(replacementRecording.isCompleted)

        finishRecord.complete(Unit)
        firstRecording.await()
        val result = deletion.await()
        replacementRecording.await()
        assertSame(saved, result.profile)
        assertSame(replacement, result.state)
        assertEquals(1, snapshotReads)
        assertEquals(listOf(connected.connectionSessionId), recorded)
        assertNull(persisted)
        coordinator.recordConnection(replacement)
        assertEquals(listOf(connected.connectionSessionId), recorded)
        assertNull(persisted)
    }

    @Test
    fun `delete during initialization suppresses a connected record queued during its commit`() = runTest {
        val enteredDelete = CompletableDeferred<Unit>()
        val finishDelete = CompletableDeferred<Unit>()
        var persisted: CoolerProfile? = saved
        var records = 0
        val coordinator = ProfileMutationCoordinator(
            scope = backgroundScope,
            record = { records++; persisted = saved },
            delete = {
                enteredDelete.complete(Unit)
                finishDelete.await()
                persisted.also { persisted = null }
            },
        )
        val deletion = async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.deleteProfile(saved.id, connected.copy(connection = ConnectionState.CONNECTING))
        }
        enteredDelete.await()
        val recording = async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.recordConnection(connected)
        }
        runCurrent()
        assertEquals(0, records)
        assertFalse(recording.isCompleted)
        assertSame(saved, persisted)

        finishDelete.complete(Unit)
        assertSame(saved, deletion.await())
        recording.await()
        assertNull(persisted)
        assertEquals(0, records)
    }

    @Test
    fun `failed deletion does not suppress the queued record and the lane remains usable`() = runTest {
        val enteredDelete = CompletableDeferred<Unit>()
        val failDelete = CompletableDeferred<Unit>()
        val failure = IOException("disk write failed")
        val events = mutableListOf<String>()
        val coordinator = ProfileMutationCoordinator(
            scope = backgroundScope,
            record = { events += "record committed" },
            delete = {
                events += "delete started"
                enteredDelete.complete(Unit)
                failDelete.await()
                events += "delete failed"
                throw failure
            },
        )
        val deletion = async(start = CoroutineStart.UNDISPATCHED) {
            expectFailure<IOException> { coordinator.deleteProfile(saved.id, connected) }
        }
        enteredDelete.await()
        val recording = async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.recordConnection(connected)
        }
        runCurrent()
        assertFalse(recording.isCompleted)
        failDelete.complete(Unit)
        assertSame(failure, deletion.await())
        recording.await()
        assertEquals(listOf("delete started", "delete failed", "record committed"), events)
    }

    @Test
    fun `real repository deletion returns the committed object and clears its references atomically`() = runTest {
        val other = saved.copy(id = "other", macAddress = "11:22:33:44:55:66")
        val store = TestPreferencesStore(profilePreferences(
            saved, other, active = saved.id, service = saved.id, default = saved.id,
        ))
        val repository = ProfileRepository(store)
        val coordinator = ProfileMutationCoordinator(
            scope = backgroundScope,
            record = { repository.recordConnection(it) },
            delete = repository::delete,
        )

        assertEquals(saved, coordinator.deleteProfile(saved.id, connected))
        val committed = store.commits.single()
        assertNull(committed[activeKey])
        assertNull(committed[serviceKey])
        assertNull(committed[defaultKey])
        assertEquals(listOf(other), repository.profiles.first())
        coordinator.recordConnection(connected)
        assertEquals(1, store.commits.size)
        assertEquals(listOf(other), repository.profiles.first())
    }

    @Test
    fun `missing deletion returns null and clears stale references without suppressing recording`() = runTest {
        val missingId = "missing"
        val store = TestPreferencesStore(profilePreferences(
            saved, active = missingId, service = missingId, default = missingId,
        ))
        val repository = ProfileRepository(store)
        val coordinator = ProfileMutationCoordinator(
            scope = backgroundScope,
            record = { repository.recordConnection(it) },
            delete = repository::delete,
        )

        assertNull(coordinator.deleteProfile(missingId, connected))
        val committed = store.commits.single()
        assertNull(committed[activeKey])
        assertNull(committed[serviceKey])
        assertNull(committed[defaultKey])
        assertEquals(listOf(saved), repository.profiles.first())
        coordinator.recordConnection(connected)
        assertEquals(2, store.commits.size)
        assertEquals(saved.id, repository.loadActiveProfile()?.id)
    }

    @Test
    fun `suppression uses the actual deleted address rather than the requested session alone`() = runTest {
        val actual = saved.copy(macAddress = "11:22:33:44:55:66")
        val repository = ProfileRepository(TestPreferencesStore(profilePreferences(actual)))
        var records = 0
        val coordinator = ProfileMutationCoordinator(
            scope = backgroundScope,
            record = { records++; repository.recordConnection(it) },
            delete = repository::delete,
        )

        assertEquals(actual, coordinator.deleteProfile(saved.id, connected))
        coordinator.recordConnection(connected)
        assertEquals(1, records)
        assertEquals(connected.deviceAddress, repository.profiles.first().single().macAddress)
    }

    @Test
    fun `same address matches case insensitively but a new session is still recordable`() = runTest {
        val recorded = mutableListOf<Long>()
        val coordinator = ProfileMutationCoordinator(
            scope = backgroundScope,
            record = { recorded += it.connectionSessionId },
            delete = { saved },
        )
        val lowercase = connected.copy(deviceAddress = saved.macAddress.lowercase(Locale.ROOT))
        assertSame(saved, coordinator.deleteProfile(saved.id, lowercase))
        coordinator.recordConnection(connected)
        coordinator.recordConnection(connected.copy(connectionSessionId = 2L))
        coordinator.recordConnection(lowercase) // A late old-session event remains ignored.
        assertEquals(listOf(2L), recorded)
    }

    @Test
    fun `reconnect during suspended deletion is suppressed before queued record can recreate profile`() = runTest {
        val enteredDelete = CompletableDeferred<Unit>()
        val finishDelete = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        var persisted: CoolerProfile? = saved
        var current = connected
        val coordinator = ProfileMutationCoordinator(
            scope = backgroundScope,
            record = { state -> events += "record ${state.connectionSessionId}"; persisted = saved },
            delete = {
                events += "delete started"
                enteredDelete.complete(Unit)
                finishDelete.await()
                events += "delete committed"
                persisted.also { persisted = null }
            },
        )
        val deletion = async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.deleteProfile(saved.id) { current }
        }
        enteredDelete.await()
        current = connected.copy(connectionSessionId = 2L)
        val recording = async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.recordConnection(current)
        }
        runCurrent()
        assertFalse(recording.isCompleted)
        finishDelete.complete(Unit)
        assertEquals(saved, deletion.await().profile)
        recording.await()
        assertEquals(listOf("delete started", "delete committed"), events)
        assertNull(persisted)
    }

    @Test
    fun `cancelling the waiter does not let deletion overtake an already started record`() = runTest {
        val enteredRecord = CompletableDeferred<Unit>()
        val finishRecord = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        var persisted: CoolerProfile? = null
        val coordinator = ProfileMutationCoordinator(
            scope = backgroundScope,
            record = {
                events += "record started"
                enteredRecord.complete(Unit)
                finishRecord.await()
                persisted = saved
                events += "record committed"
            },
            delete = {
                events += "delete committed"
                persisted.also { persisted = null }
            },
        )
        val waiter = launch(start = CoroutineStart.UNDISPATCHED) {
            coordinator.recordConnection(connected)
        }
        enteredRecord.await()
        waiter.cancelAndJoin()
        val deletion = async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.deleteProfile(saved.id, connected)
        }
        runCurrent()
        assertEquals(listOf("record started"), events)
        assertFalse(deletion.isCompleted)

        finishRecord.complete(Unit)
        assertSame(saved, deletion.await())
        assertEquals(listOf("record started", "record committed", "delete committed"), events)
        assertNull(persisted)
    }

    @Test
    fun `cancelling a started delete waiter still commits suppression before queued recording`() = runTest {
        val enteredDelete = CompletableDeferred<Unit>()
        val finishDelete = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        var persisted: CoolerProfile? = saved
        val coordinator = ProfileMutationCoordinator(
            scope = backgroundScope,
            record = { events += "record committed"; persisted = saved },
            delete = {
                events += "delete started"
                enteredDelete.complete(Unit)
                finishDelete.await()
                events += "delete committed"
                persisted.also { persisted = null }
            },
        )
        val waiter = launch(start = CoroutineStart.UNDISPATCHED) {
            coordinator.deleteProfile(saved.id, connected)
        }
        enteredDelete.await()
        waiter.cancelAndJoin()
        val recording = async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.recordConnection(connected)
        }
        runCurrent()
        assertEquals(listOf("delete started"), events)
        assertFalse(recording.isCompleted)

        finishDelete.complete(Unit)
        recording.await()
        assertEquals(listOf("delete started", "delete committed"), events)
        assertNull(persisted)
    }

    private suspend fun ProfileMutationCoordinator.deleteProfile(
        id: String,
        state: CoolerLiveState,
    ): CoolerProfile? = deleteProfile(id) { state }.profile
}
