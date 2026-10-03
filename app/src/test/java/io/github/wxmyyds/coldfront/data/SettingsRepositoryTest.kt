package io.github.wxmyyds.coldfront.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class SettingsRepositoryTest {
    private val darkModeKey = stringPreferencesKey("dark_mode")
    private val paletteKey = stringPreferencesKey("palette")
    private val languageKey = stringPreferencesKey("app_language")

    @Test
    fun `empty preferences expose existing defaults`() = runTest {
        val repository = SettingsRepository(TestPreferencesStore())
        assertFalse(repository.dynamicColor.first())
        assertTrue(repository.predictiveBack.first())
        assertEquals("system", repository.darkMode.first())
        assertEquals("tonal_spot", repository.palette.first())
        assertEquals("system", repository.appLanguage.first())
    }

    @Test
    fun `invalid stored enum-like settings expose fallback without rewriting preferences`() = runTest {
        val initial = mutablePreferencesOf(
            darkModeKey to "DARK",
            paletteKey to "future_palette",
            languageKey to "fr",
        ).toPreferences()
        val store = TestPreferencesStore(initial)
        val repository = SettingsRepository(store)
        assertEquals("system", repository.darkMode.first())
        assertEquals("tonal_spot", repository.palette.first())
        assertEquals("system", repository.appLanguage.first())
        assertEquals(initial, store.snapshot)
        assertTrue(store.commits.isEmpty())
    }

    @Test
    fun `supported settings roundtrip and invalid setters persist fallback defaults`() = runTest {
        val store = TestPreferencesStore()
        val repository = SettingsRepository(store)
        listOf("system", "light", "dark").forEach { mode ->
            repository.setDarkMode(mode)
            assertEquals(mode, repository.darkMode.first())
        }
        listOf("tonal_spot", "neutral", "vibrant", "expressive", "rainbow",
            "fruit_salad", "monochrome", "fidelity", "content").forEach { palette ->
            repository.setPalette(palette)
            assertEquals(palette, repository.palette.first())
        }
        listOf("system", "zh", "en").forEach { language ->
            repository.setAppLanguage(language)
            assertEquals(language, repository.appLanguage.first())
        }
        listOf("", "unsupported", " SYSTEM ").forEach { invalid ->
            repository.setDarkMode(invalid)
            repository.setPalette(invalid)
            repository.setAppLanguage(invalid)
            assertEquals("system", store.snapshot[darkModeKey])
            assertEquals("tonal_spot", store.snapshot[paletteKey])
            assertEquals("system", store.snapshot[languageKey])
        }
        repository.setDynamicColor(true)
        repository.setPredictiveBack(false)
        assertTrue(repository.dynamicColor.first())
        assertFalse(repository.predictiveBack.first())
        assertEquals(true, store.snapshot[booleanPreferencesKey("dynamic_color")])
        assertEquals(false, store.snapshot[booleanPreferencesKey("predictive_back")])
    }

    @Test
    fun `settings writes leave orphan thermal values and unknown preferences intact`() = runTest {
        val orphanKeys = listOf(
            "low_temp", "mid_temp", "high_temp", "condensation_temp",
            "low_speed", "mid_speed", "high_speed",
        ).map(::intPreferencesKey)
        val initial = mutablePreferencesOf(stringPreferencesKey("future_setting") to "retain")
        orphanKeys.forEachIndexed { index, key -> initial[key] = index + 10 }
        val store = TestPreferencesStore(initial)
        val repository = SettingsRepository(store)
        repository.setDarkMode("dark")
        repository.setPalette("vibrant")
        repository.setAppLanguage("en")
        repository.setDynamicColor(true)
        repository.setPredictiveBack(false)
        orphanKeys.forEachIndexed { index, key -> assertEquals(index + 10, store.snapshot[key]) }
        assertEquals("retain", store.snapshot[stringPreferencesKey("future_setting")])
    }

    @Test
    fun `unrelated snapshots do not emit duplicate validated settings`() = runTest {
        // A cold scripted flow ensures distinctness is tested without StateFlow conflation.
        val store = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow {
                emit(mutablePreferencesOf(darkModeKey to "dark"))
                emit(mutablePreferencesOf(darkModeKey to "dark", paletteKey to "neutral"))
                emit(mutablePreferencesOf(darkModeKey to "invalid"))
                emit(mutablePreferencesOf(darkModeKey to "also_invalid"))
                emit(mutablePreferencesOf(darkModeKey to "light"))
            }
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                error("No write expected")
        }
        val observed = SettingsRepository(store).darkMode.toList()
        assertEquals(listOf("dark", "system", "light"), observed)
    }

    @Test
    fun `settings read write errors and cancellation propagate unchanged`() = runTest {
        listOf(IOException("disk failure"), CancellationException("cancelled")).forEach { failure ->
            val store = object : DataStore<Preferences> {
                override val data: Flow<Preferences> = flow { throw failure }
                override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                    throw failure
            }
            val repository = SettingsRepository(store)
            assertSame(failure, expectFailure<Exception> { repository.dynamicColor.first() })
            assertSame(failure, expectFailure<Exception> { repository.darkMode.first() })
            assertSame(failure, expectFailure<Exception> { repository.palette.first() })
            assertSame(failure, expectFailure<Exception> { repository.predictiveBack.first() })
            assertSame(failure, expectFailure<Exception> { repository.appLanguage.first() })
            assertSame(failure, expectFailure<Exception> { repository.setDynamicColor(true) })
            assertSame(failure, expectFailure<Exception> { repository.setDarkMode("dark") })
            assertSame(failure, expectFailure<Exception> { repository.setPalette("neutral") })
            assertSame(failure, expectFailure<Exception> { repository.setPredictiveBack(false) })
            assertSame(failure, expectFailure<Exception> { repository.setAppLanguage("en") })
        }
    }
}
