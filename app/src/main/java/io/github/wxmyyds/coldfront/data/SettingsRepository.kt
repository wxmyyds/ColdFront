package io.github.wxmyyds.coldfront.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.wxmyyds.coldfront.ui.theme.PaletteStyles
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "cooler_settings")

class SettingsRepository internal constructor(private val dataStore: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.settingsDataStore)

    /** 动态取色默认关闭，使用应用自带的紫灰主题；用户可在设置中开启。 */
    val dynamicColor: Flow<Boolean> = dataStore.data.map {
        it[KEY_DYNAMIC_COLOR] ?: false
    }.distinctUntilChanged()

    /** 深色模式: system / light / dark */
    val darkMode: Flow<String> = dataStore.data.map {
        darkModeOrDefault(it[KEY_DARK_MODE])
    }.distinctUntilChanged()

    val palette: Flow<String> = dataStore.data.map {
        paletteOrDefault(it[KEY_PALETTE])
    }.distinctUntilChanged()

    val predictiveBack: Flow<Boolean> = dataStore.data.map {
        it[KEY_PREDICTIVE_BACK] ?: true
    }.distinctUntilChanged()

    /** 界面语言: system / zh / en */
    val appLanguage: Flow<String> = dataStore.data.map {
        languageOrDefault(it[KEY_APP_LANGUAGE])
    }.distinctUntilChanged()

    suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { it[KEY_DYNAMIC_COLOR] = enabled }
    }

    suspend fun setDarkMode(mode: String) {
        dataStore.edit { it[KEY_DARK_MODE] = darkModeOrDefault(mode) }
    }

    suspend fun setPalette(palette: String) {
        dataStore.edit { it[KEY_PALETTE] = paletteOrDefault(palette) }
    }

    suspend fun setPredictiveBack(enabled: Boolean) {
        dataStore.edit { it[KEY_PREDICTIVE_BACK] = enabled }
    }

    suspend fun setAppLanguage(language: String) {
        dataStore.edit { it[KEY_APP_LANGUAGE] = languageOrDefault(language) }
    }

    private fun darkModeOrDefault(value: String?): String = when (value) {
        "system", "light", "dark" -> value
        else -> "system"
    }

    private fun paletteOrDefault(value: String?): String =
        if (PaletteStyles.isValid(value)) requireNotNull(value) else PaletteStyles.DEFAULT

    private fun languageOrDefault(value: String?): String = when (value) {
        "system", "zh", "en" -> value
        else -> "system"
    }

    companion object {
        private val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        private val KEY_DARK_MODE = stringPreferencesKey("dark_mode")
        private val KEY_PALETTE = stringPreferencesKey("palette")
        private val KEY_PREDICTIVE_BACK = booleanPreferencesKey("predictive_back")
        private val KEY_APP_LANGUAGE = stringPreferencesKey("app_language")
        // Former thermal-threshold preferences are intentionally left untouched on disk.
    }
}
