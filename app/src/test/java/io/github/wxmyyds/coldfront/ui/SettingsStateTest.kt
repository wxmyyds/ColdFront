package io.github.wxmyyds.coldfront.ui

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.wxmyyds.coldfront.data.AppSettings
import io.github.wxmyyds.coldfront.data.SettingsRepository
import io.github.wxmyyds.coldfront.data.expectFailure
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsStateTest {
    private val first = AppSettings(true, "dark", "vibrant", false, "en")
    private val second = AppSettings(false, "light", "neutral", true, "zh")

    @Test
    fun `first read failure reports while loading then retries into one complete ready snapshot`() = runTest {
        val releaseFailure = CompletableDeferred<Unit>()
        val snapshots = Channel<Preferences>(Channel.UNLIMITED)
        val failure = IOException("first disk read")
        var subscriptions = 0
        val repository = repository(flow {
            subscriptions++
            if (subscriptions == 1) {
                releaseFailure.await()
                throw failure
            }
            for (snapshot in snapshots) emit(snapshot)
        })
        val errors = mutableListOf<IOException>()
        // Exercise the exact helper used by the VM, including the nullable StateFlow initial value.
        val state = repository.settings.storageStateIn<AppSettings?>(backgroundScope, null, errors::add)
        val observed = mutableListOf<AppSettings?>()
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { state.toList(observed) }

        runCurrent()
        assertEquals(1, subscriptions)
        assertEquals(listOf<AppSettings?>(null), observed)
        releaseFailure.complete(Unit)
        runCurrent()
        assertSame(failure, errors.single())
        assertNull(state.value)

        advanceTimeBy(1_999)
        runCurrent()
        assertEquals(1, subscriptions)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(2, subscriptions)
        assertNull(state.value) // Retry has started, but no successful snapshot yet.

        snapshots.send(preferences(first))
        runCurrent()
        assertEquals(first, state.value)
        assertEquals(listOf(null, first), observed) // All five fields are non-default on first ready.
        assertEquals(1, errors.size)
    }

    @Test
    fun `two disk snapshots never publish mixed settings or independent readiness`() = runTest {
        val snapshots = Channel<Preferences>(Channel.UNLIMITED)
        var subscriptions = 0
        val repository = repository(flow {
            subscriptions++
            for (snapshot in snapshots) emit(snapshot)
        })
        val state = repository.settings.storageStateIn<AppSettings?>(backgroundScope, null) {
            throw AssertionError("Unexpected storage failure", it)
        }
        val observed = mutableListOf<AppSettings?>()
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { state.toList(observed) }
        runCurrent()

        snapshots.send(preferences(first))
        runCurrent()
        snapshots.send(preferences(second))
        runCurrent()

        assertEquals(1, subscriptions)
        assertEquals(listOf(null, first, second), observed)
        assertEquals(second, state.value)
    }

    @Test
    fun `a later read failure preserves the last ready snapshot until retry succeeds`() = runTest {
        val failRead = CompletableDeferred<Unit>()
        val nextRead = CompletableDeferred<Unit>()
        val errors = mutableListOf<IOException>()
        var subscriptions = 0
        val repository = repository(flow {
            subscriptions++
            if (subscriptions == 1) {
                emit(preferences(first))
                failRead.await()
                throw IOException("subsequent read")
            }
            nextRead.await()
            emit(preferences(second))
        })
        val state = repository.settings.storageStateIn<AppSettings?>(backgroundScope, null, errors::add)
        runCurrent()
        assertEquals(first, state.value)
        failRead.complete(Unit)
        runCurrent()
        assertEquals(first, state.value)
        assertEquals(1, errors.size)
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(2, subscriptions)
        assertEquals(first, state.value)
        nextRead.complete(Unit)
        runCurrent()
        assertEquals(second, state.value)
    }

    @Test
    fun `upstream cancellation and non IO failures propagate unchanged without reporting or retry`() = runTest {
        listOf(CancellationException("cancel read"), IllegalStateException("bad data"))
            .forEach { failure ->
                var subscriptions = 0
                val errors = mutableListOf<IOException>()
                val reads = flow<AppSettings> {
                    subscriptions++
                    throw failure
                }.retryStorageReads(errors::add)

                assertSame(failure, expectFailure<Exception> { reads.first() })
                assertEquals(1, subscriptions)
                assertTrue(errors.isEmpty())
            }
    }

    @Test
    fun `cancelling state collection during the retry delay prevents another disk read`() = runTest {
        val sharingJob = Job(backgroundScope.coroutineContext[Job])
        val scope = CoroutineScope(backgroundScope.coroutineContext + sharingJob)
        var subscriptions = 0
        val errors = mutableListOf<IOException>()
        try {
            val state = flow<AppSettings> {
                subscriptions++
                throw IOException("offline storage")
            }.storageStateIn<AppSettings?>(scope, null, errors::add)
            runCurrent()
            assertEquals(1, subscriptions)
            assertEquals(1, errors.size)
            scope.cancel()
            advanceTimeBy(10_000)
            runCurrent()
            assertTrue(sharingJob.isCancelled)
            assertEquals(1, subscriptions)
            assertEquals(1, errors.size)
            assertNull(state.value)
        } finally {
            scope.cancel()
        }
    }

    private fun preferences(settings: AppSettings): Preferences = mutablePreferencesOf(
        booleanPreferencesKey("dynamic_color") to settings.dynamicColor,
        stringPreferencesKey("dark_mode") to settings.darkMode,
        stringPreferencesKey("palette") to settings.palette,
        booleanPreferencesKey("predictive_back") to settings.predictiveBack,
        stringPreferencesKey("app_language") to settings.appLanguage,
    ).toPreferences()

    private fun repository(snapshots: Flow<Preferences>) = SettingsRepository(object : DataStore<Preferences> {
        override val data: Flow<Preferences> = snapshots
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            error("No writes expected")
    })
}
