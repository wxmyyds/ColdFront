package io.github.wxmyyds.coldfront.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import io.github.wxmyyds.coldfront.domain.ConnectionState
import io.github.wxmyyds.coldfront.domain.CoolerDeviceType
import io.github.wxmyyds.coldfront.domain.CoolerLiveState
import io.github.wxmyyds.coldfront.domain.CoolerProfile
import io.github.wxmyyds.coldfront.domain.FanMode
import io.github.wxmyyds.coldfront.domain.LightEffect
import io.github.wxmyyds.coldfront.domain.RGBConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ProfileRepositoryTest {
    private fun connected(
        address: String? = TEST_MAC,
        name: String? = "Scan name",
        session: Long = 1L,
    ) = CoolerLiveState(
        connection = ConnectionState.CONNECTED,
        deviceType = CoolerDeviceType.JACKET_8_PRO,
        deviceAddress = address,
        deviceName = name,
        connectionSessionId = session,
    )

    @Test
    fun `only a successfully connected valid address and type may be saved`() = runTest {
        val store = TestPreferencesStore()
        val repository = ProfileRepository(store)
        ConnectionState.entries.filter { it != ConnectionState.CONNECTED }.forEach { connection ->
            assertNull(repository.recordConnection(connected().copy(connection = connection)))
        }
        assertNull(repository.recordConnection(connected().copy(deviceType = null)))
        listOf(null, "", "not a MAC", "AA:BB:CC:DD:EE", "GG:BB:CC:DD:EE:FF").forEach { address ->
            assertNull(repository.recordConnection(connected(address = address)))
        }
        assertTrue(store.commits.isEmpty())
        assertTrue(repository.profiles.first().isEmpty())
    }

    @Test
    fun `normal connection saves and activates in one commit without opting into service`() = runTest {
        val store = TestPreferencesStore()
        val repository = ProfileRepository(store)
        val before = System.currentTimeMillis()
        val saved = repository.recordConnection(connected(address = " aa:bb:cc:dd:ee:ff "))!!

        assertEquals("Scan name", saved.name)
        assertEquals(TEST_MAC, saved.macAddress)
        assertEquals(CoolerDeviceType.JACKET_8_PRO, saved.deviceType)
        assertTrue(saved.createdAtMs >= before)
        assertEquals(saved.createdAtMs, saved.lastConnectedAtMs)
        assertEquals(50, saved.fanPercent)
        assertEquals(FanMode.MANUAL, saved.fanMode)
        assertEquals(1, store.commits.size)
        assertEquals(saved.id, store.commits.single()[activeKey])
        assertEquals(listOf(saved), repository.profiles.first())
        assertEquals(saved, repository.loadActiveProfile())
        assertNull(store.snapshot[serviceKey])
        assertNull(repository.loadServiceProfile())
    }

    @Test
    fun `raw connection without scan name persists using the model display name`() = runTest {
        val repository = ProfileRepository(TestPreferencesStore())
        val saved = repository.recordConnection(connected(name = null))!!
        assertEquals(CoolerDeviceType.JACKET_8_PRO.deviceName, saved.name)
        assertEquals(saved, repository.loadActiveProfile())
    }

    @Test
    fun `normal raw and saved reconnects reuse identity and preserve user settings`() = runTest {
        val original = profile(address = "aa:bb:cc:dd:ee:ff").copy(
            name = "Custom name",
            fanPercent = 73,
            fanMode = FanMode.AUTO,
            rgb = RGBConfig(LightEffect.BREATH_SINGLE, 12, 34, 56),
        )
        val store = TestPreferencesStore(profilePreferences(original))
        val repository = ProfileRepository(store)
        // These are the states supplied by normal discovery, raw address, and saved-profile paths.
        listOf("Normal scan", null, "Saved connection").forEachIndexed { index, name ->
            val before = System.currentTimeMillis()
            val saved = repository.recordConnection(connected(name = name, session = index + 1L))!!
            assertEquals(original.id, saved.id)
            assertEquals(original.name, saved.name)
            assertEquals(original.createdAtMs, saved.createdAtMs)
            assertEquals(original.fanPercent, saved.fanPercent)
            assertEquals(original.fanMode, saved.fanMode)
            assertEquals(original.rgb, saved.rgb)
            assertEquals(CoolerDeviceType.JACKET_8_PRO, saved.deviceType)
            assertTrue(saved.lastConnectedAtMs >= before)
            assertEquals(listOf(saved), repository.profiles.first())
            assertEquals(saved, repository.loadActiveProfile())
        }
        assertEquals(3, store.commits.size)
    }

    @Test
    fun `dedupe prefers active identity and retargets opted in same device service`() = runTest {
        val oldest = profile(id = "old", createdAt = 1L)
        val active = profile(id = "active", address = "aa:bb:cc:dd:ee:ff", createdAt = 2L)
            .copy(name = "Retain this name", fanPercent = 81, fanMode = FanMode.AUTO)
        val unrelated = profile(id = "unrelated", address = OTHER_MAC)
        val store = TestPreferencesStore(profilePreferences(
            oldest, active, unrelated, active = active.id, service = oldest.id,
        ))
        val repository = ProfileRepository(store)
        val saved = repository.recordConnection(connected())!!

        assertEquals(active.id, saved.id)
        assertEquals(active.name, saved.name)
        assertEquals(active.createdAtMs, saved.createdAtMs)
        assertEquals(active.fanPercent, saved.fanPercent)
        assertEquals(active.fanMode, saved.fanMode)
        assertEquals(listOf(saved, unrelated), repository.profiles.first())
        assertEquals(saved, repository.loadServiceProfile())
        assertEquals(saved.id, store.snapshot[serviceKey])
        assertEquals(1, store.commits.size)
    }

    @Test
    fun `dedupe retargets a startup default pointing at the removed duplicate`() = runTest {
        val oldest = profile(id = "old", createdAt = 1L)
        val active = profile(id = "active", address = "aa:bb:cc:dd:ee:ff", createdAt = 2L)
        val store = TestPreferencesStore(profilePreferences(
            oldest, active, active = active.id, default = oldest.id,
        ))
        val repository = ProfileRepository(store)
        val saved = repository.recordConnection(connected())!!

        assertEquals(active.id, saved.id)
        assertEquals(saved.id, store.snapshot[defaultKey])
        assertEquals(saved, repository.loadDefaultProfile())
    }

    @Test
    fun `dedupe keeps oldest same MAC when active profile is another device`() = runTest {
        val newer = profile(id = "newer", createdAt = 9L)
        val oldest = profile(id = "oldest", address = " Aa:Bb:Cc:Dd:Ee:Ff ", createdAt = 1L)
        val unrelated = profile(id = "unrelated", address = OTHER_MAC)
        val store = TestPreferencesStore(profilePreferences(
            newer, oldest, unrelated, active = unrelated.id, service = unrelated.id,
        ))
        val repository = ProfileRepository(store)
        val saved = repository.recordConnection(connected())!!
        assertEquals(oldest.id, saved.id)
        assertEquals(oldest.createdAtMs, saved.createdAtMs)
        assertEquals(2, repository.profiles.first().size)
        assertEquals(unrelated, repository.loadServiceProfile())
        assertEquals(saved, repository.loadActiveProfile())
    }

    @Test
    fun `concurrent same device records cannot create duplicate identities`() = runTest {
        val store = TestPreferencesStore()
        val repository = ProfileRepository(store)
        val records = (1L..12L).map { session ->
            async { repository.recordConnection(connected(session = session))!! }
        }.awaitAll()
        assertEquals(1, records.map { it.id }.distinct().size)
        assertEquals(1, repository.profiles.first().size)
        store.commits.forEach { snapshot ->
            val profiles = ProfileJson.parse(snapshot[profilesKey]).profiles
            assertEquals(profiles.single().id, snapshot[activeKey])
        }
    }

    @Test
    fun `active and service lookup each use a single preferences snapshot`() = runTest {
        val first = profile(id = "first")
        val second = profile(id = "second", address = OTHER_MAC)
        var collections = 0
        val store = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow {
                val current = if (collections++ == 0) first else second
                emit(profilePreferences(current, active = current.id, service = current.id))
            }
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                error("No write expected")
        }
        val repository = ProfileRepository(store)
        assertEquals(first, repository.loadActiveProfile())
        assertEquals(1, collections)
        assertEquals(second, repository.loadServiceProfile())
        assertEquals(2, collections)
    }

    @Test
    fun `missing service preference stays off even with an active profile`() = runTest {
        val original = profile()
        val store = TestPreferencesStore(profilePreferences(original, active = original.id))
        val repository = ProfileRepository(store)
        assertEquals(original, repository.loadActiveProfile())
        assertNull(repository.loadServiceProfile())
        assertNull(repository.serviceProfile.first())
        repository.setServiceProfile(original.id)
        assertEquals(original, repository.loadServiceProfile())
        assertEquals(original, repository.serviceProfile.first())
        repository.setServiceProfile(null)
        assertNull(store.snapshot[serviceKey])
        assertNull(repository.loadServiceProfile())
        assertEquals(original, repository.loadActiveProfile())
    }

    @Test
    fun `missing service target rejects with IOException and does not alter intent`() = runTest {
        val original = profile()
        val store = TestPreferencesStore(profilePreferences(original, service = original.id))
        val repository = ProfileRepository(store)
        expectFailure<IOException> { repository.setServiceProfile("missing") }
        assertTrue(store.commits.isEmpty())
        assertEquals(original, repository.loadServiceProfile())

        val stale = ProfileRepository(TestPreferencesStore(profilePreferences(service = "deleted")))
        assertNull(stale.loadServiceProfile())
        assertNull(stale.serviceProfile.first())
    }

    @Test
    fun `startup default is stored read and cleared without touching active or service`() = runTest {
        val original = profile()
        val other = profile(id = "other", address = OTHER_MAC)
        val store = TestPreferencesStore(profilePreferences(
            original, other, active = original.id, service = original.id,
        ))
        val repository = ProfileRepository(store)
        assertNull(repository.loadDefaultProfile())
        assertNull(repository.defaultProfile.first())

        repository.setDefaultProfile(other.id)
        assertEquals(other, repository.loadDefaultProfile())
        assertEquals(other, repository.defaultProfile.first())
        assertEquals(other.id, store.snapshot[defaultKey])
        assertEquals(original, repository.loadActiveProfile())
        assertEquals(original, repository.loadServiceProfile())

        repository.setDefaultProfile(null)
        assertNull(store.snapshot[defaultKey])
        assertNull(repository.loadDefaultProfile())
        assertEquals(original, repository.loadActiveProfile())
        assertEquals(original, repository.loadServiceProfile())
    }

    @Test
    fun `missing default target rejects with IOException and does not alter intent`() = runTest {
        val original = profile()
        val store = TestPreferencesStore(profilePreferences(original, default = original.id))
        val repository = ProfileRepository(store)
        expectFailure<IOException> { repository.setDefaultProfile("missing") }
        assertTrue(store.commits.isEmpty())
        assertEquals(original, repository.loadDefaultProfile())

        val stale = ProfileRepository(TestPreferencesStore(profilePreferences(default = "deleted")))
        assertNull(stale.loadDefaultProfile())
        assertNull(stale.defaultProfile.first())
    }

    @Test
    fun `delete atomically clears active and service references and emits null service`() = runTest {
        val original = profile()
        val other = profile(id = "other", address = OTHER_MAC)
        val store = TestPreferencesStore(profilePreferences(
            original, other, active = original.id, service = original.id,
        ))
        val repository = ProfileRepository(store)
        val observed = mutableListOf<CoolerProfile?>()
        val collection = launch(start = CoroutineStart.UNDISPATCHED) {
            repository.serviceProfile.take(2).toList(observed)
        }
        repository.delete(original.id)
        collection.join()

        assertEquals(listOf(original, null), observed)
        assertEquals(1, store.commits.size)
        val committed = store.commits.single()
        assertNull(committed[activeKey])
        assertNull(committed[serviceKey])
        assertEquals(listOf(other), ProfileJson.parse(committed[profilesKey]).profiles)
        assertNull(repository.loadActiveProfile())
        assertNull(repository.loadServiceProfile())
        expectFailure<IOException> { repository.setServiceProfile(original.id) }
    }

    @Test
    fun `deleting unrelated profile does not clear active or service`() = runTest {
        val retained = profile()
        val other = profile(id = "other", address = OTHER_MAC)
        val store = TestPreferencesStore(profilePreferences(
            retained, other, active = retained.id, service = retained.id,
        ))
        val repository = ProfileRepository(store)
        repository.delete(other.id)
        assertEquals(retained, repository.loadActiveProfile())
        assertEquals(retained, repository.loadServiceProfile())
        assertEquals(listOf(retained), repository.profiles.first())
    }

    @Test
    fun `delete clears a startup default pointing at the removed profile`() = runTest {
        val original = profile()
        val other = profile(id = "other", address = OTHER_MAC)
        val store = TestPreferencesStore(profilePreferences(
            original, other, active = original.id, service = other.id, default = original.id,
        ))
        val repository = ProfileRepository(store)
        val observed = mutableListOf<CoolerProfile?>()
        val collection = launch(start = CoroutineStart.UNDISPATCHED) {
            repository.defaultProfile.take(2).toList(observed)
        }
        repository.delete(original.id)
        collection.join()

        assertEquals(listOf(original, null), observed)
        assertEquals(1, store.commits.size)
        val committed = store.commits.single()
        assertNull(committed[defaultKey])
        assertNull(committed[activeKey])
        assertEquals(other.id, committed[serviceKey])
        assertNull(repository.loadDefaultProfile())
        expectFailure<IOException> { repository.setDefaultProfile(original.id) }
    }

    @Test
    fun `deleting unrelated profile does not clear a startup default`() = runTest {
        val retained = profile()
        val other = profile(id = "other", address = OTHER_MAC)
        val store = TestPreferencesStore(profilePreferences(
            retained, other, default = retained.id,
        ))
        val repository = ProfileRepository(store)
        repository.delete(other.id)
        assertEquals(retained, repository.loadDefaultProfile())
    }

    @Test
    fun `corrupt document rejects reads and profile mutations without replacing stored data`() = runTest {
        listOf("", " ", "{}", "null", "[", "[{\"id\":", "[] trailing", "[] []").forEach { corrupt ->
            val initial = mutablePreferencesOf(
                profilesKey to corrupt, activeKey to "saved", serviceKey to "saved",
            ).toPreferences()
            val store = TestPreferencesStore(initial)
            val repository = ProfileRepository(store)
            expectFailure<IOException> { repository.profiles.first() }
            expectFailure<IOException> { repository.loadActiveProfile() }
            expectFailure<IOException> { repository.loadServiceProfile() }
            expectFailure<IOException> { repository.serviceProfile.first() }
            expectFailure<IOException> { repository.recordConnection(connected()) }
            expectFailure<IOException> { repository.delete("saved") }
            expectFailure<IOException> { repository.setServiceProfile("saved") }
            assertEquals(initial, store.snapshot)
            assertTrue(store.commits.isEmpty())
            // Users must still be able to opt out without overwriting the broken profile document.
            repository.setServiceProfile(null)
            assertEquals(corrupt, store.snapshot[profilesKey])
            assertEquals("saved", store.snapshot[activeKey])
            assertNull(store.snapshot[serviceKey])
        }
    }

    @Test
    fun `DataStore IO failures and cancellation propagate without wrapping or swallowing`() = runTest {
        listOf(IOException("disk failure"), CancellationException("cancelled")).forEach { failure ->
            val store = object : DataStore<Preferences> {
                override val data: Flow<Preferences> = flow { throw failure }
                override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                    throw failure
            }
            val repository = ProfileRepository(store)
            assertSame(failure, expectFailure<Exception> { repository.profiles.first() })
            assertSame(failure, expectFailure<Exception> { repository.serviceProfile.first() })
            assertSame(failure, expectFailure<Exception> { repository.loadActiveProfile() })
            assertSame(failure, expectFailure<Exception> { repository.recordConnection(connected()) })
            assertSame(failure, expectFailure<Exception> { repository.delete("saved") })
            assertSame(failure, expectFailure<Exception> { repository.setServiceProfile("saved") })
            assertSame(failure, expectFailure<Exception> { repository.setServiceProfile(null) })
        }
    }
}
