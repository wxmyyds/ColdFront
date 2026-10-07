package io.github.wxmyyds.coldfront.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import io.github.wxmyyds.coldfront.domain.CoolerProfile
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileObservationTest {
    @Test
    fun `corrupt list waits for a changed snapshot without resubscribing or inventing an empty list`() = runTest {
        val backing = TestPreferencesStore(mutablePreferencesOf(profilesKey to "["))
        val store = CountingStore(backing)
        val repository = ProfileRepository(store)
        val errors = mutableListOf<IOException>()
        val values = mutableListOf<List<CoolerProfile>>()
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            repository.observeProfiles(errors::add).retryStorageReads {
                throw AssertionError("Decode errors must not trigger read retries", it)
            }.toList(values)
        }
        assertEquals(1, errors.size)
        assertTrue(values.isEmpty())
        advanceTimeBy(20_000)
        runCurrent()
        assertEquals(1, store.collections)
        assertEquals(1, store.snapshots)
        assertEquals(1, errors.size)
        assertTrue(backing.commits.isEmpty())
        assertEquals("[", backing.snapshot[profilesKey])

        backing.updateData { mutablePreferencesOf(profilesKey to "{") }
        runCurrent()
        assertEquals(1, errors.size) // A second broken snapshot is the same failure streak.
        assertTrue(values.isEmpty())

        val saved = profile()
        backing.updateData { profilePreferences(saved) }
        runCurrent() // Repair is observed at this virtual instant, not after a retry interval.
        assertEquals(listOf(listOf(saved)), values)
        assertEquals(1, store.collections)

        backing.updateData { mutablePreferencesOf(profilesKey to "[") }
        runCurrent()
        assertEquals(2, errors.size)
        assertEquals(listOf(listOf(saved)), values) // Last usable data is not replaced by empty.
        assertEquals("[", backing.snapshot[profilesKey])
        backing.updateData { profilePreferences() }
        runCurrent()
        assertEquals(listOf(listOf(saved), emptyList<CoolerProfile>()), values)
    }

    @Test
    fun `service and default observers retain targets on corruption but clear references immediately`() = runTest {
        for (key in listOf(serviceKey, defaultKey)) {
            val saved = profile()
            val backing = TestPreferencesStore(profilePreferences(saved, service = saved.id, default = saved.id))
            val store = CountingStore(backing)
            val repository = ProfileRepository(store)
            val errors = mutableListOf<IOException>()
            val values = mutableListOf<CoolerProfile?>()
            val observation = if (key == serviceKey) repository.observeServiceProfile(errors::add)
                else repository.observeDefaultProfile(errors::add)
            val collection = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
                observation.retryStorageReads {
                    throw AssertionError("Decode errors must not trigger read retries", it)
                }.toList(values)
            }
            assertEquals(listOf(saved), values)
            val corrupt = mutablePreferencesOf(profilesKey to "[", key to saved.id)
            backing.updateData { corrupt }
            runCurrent()
            assertEquals(1, errors.size)
            assertEquals(listOf(saved), values)
            advanceTimeBy(20_000)
            runCurrent()
            assertEquals(1, store.collections)
            assertEquals(2, store.snapshots)
            assertEquals(listOf(saved), values)

            if (key == serviceKey) repository.setServiceProfile(null)
            else repository.setDefaultProfile(null)
            runCurrent()
            assertEquals(listOf(saved, null), values)
            assertEquals("[", backing.snapshot[profilesKey])
            assertEquals(1, errors.size)

            // A cleared reference was a usable OFF snapshot, so restoring a broken reference
            // is a new failure streak even though the document has never been repaired.
            backing.updateData { corrupt }
            runCurrent()
            assertEquals(2, errors.size)
            assertEquals(listOf(saved, null), values)
            backing.updateData { profilePreferences(saved, service = saved.id, default = saved.id) }
            runCurrent()
            assertEquals(listOf(saved, null, saved), values)
            backing.updateData { corrupt }
            runCurrent()
            assertEquals(3, errors.size)
            assertEquals(listOf(saved, null, saved), values)
            assertEquals(1, store.collections)
            collection.cancel()
        }
    }

    @Test
    fun `all recovering observers leave initial corrupt snapshots unknown and propagate upstream failures`() = runTest {
        val corrupt = mutablePreferencesOf(profilesKey to "[", serviceKey to "saved", defaultKey to "saved")
        val errors = mutableListOf<IOException>()
        val repository = ProfileRepository(TestPreferencesStore(corrupt))
        for (observation in observations(repository, errors::add)) {
            val values = mutableListOf<Any?>()
            val collection = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
                observation.toList(values)
            }
            assertTrue(values.isEmpty())
            collection.cancel()
        }
        assertEquals(3, errors.size)

        for (failure in listOf(IOException("disk"), CancellationException("cancel"), IllegalStateException("bug"))) {
            val failingRepository = ProfileRepository(object : DataStore<Preferences> {
                override val data: Flow<Preferences> = flow { throw failure }
                override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                    error("No write expected")
            })
            val reported = mutableListOf<IOException>()
            for (observation in observations(failingRepository, reported::add)) {
                assertSame(failure, expectFailure<Exception> { observation.first() })
            }
            assertTrue(reported.isEmpty())
        }
    }

    @Test
    fun `observer callbacks are per collection and downstream IO exceptions are never decoding failures`() = runTest {
        val saved = profile()
        val backing = TestPreferencesStore(mutablePreferencesOf(profilesKey to "["))
        val repository = ProfileRepository(backing)
        val errors = mutableListOf<IOException>()
        val observation = repository.observeProfiles(errors::add)
        val first = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { observation.collect {} }
        val second = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { observation.collect {} }
        assertEquals(2, errors.size)
        first.cancel()
        second.cancel()

        backing.updateData { profilePreferences(saved, service = saved.id, default = saved.id) }
        val downstreamFailure = IOException("consumer failed")
        errors.clear()
        for (read in observations(repository, errors::add)) {
            assertSame(downstreamFailure, expectFailure<IOException> {
                read.collect { throw downstreamFailure }
            })
        }
        assertTrue(errors.isEmpty())
    }

    private fun observations(
        repository: ProfileRepository,
        reportError: (IOException) -> Unit,
    ): List<Flow<Any?>> = listOf(
        repository.observeProfiles(reportError),
        repository.observeServiceProfile(reportError),
        repository.observeDefaultProfile(reportError),
    )

    private class CountingStore(private val backing: TestPreferencesStore) : DataStore<Preferences> by backing {
        var collections = 0
        var snapshots = 0
        override val data: Flow<Preferences> = flow {
            collections++
            backing.data.collect {
                snapshots++
                emit(it)
            }
        }
    }
}
