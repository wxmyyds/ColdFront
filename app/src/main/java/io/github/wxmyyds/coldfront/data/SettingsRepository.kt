package io.github.wxmyyds.coldfront.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "cooler_settings")

/**
 * 自动模式温控阈值与对应转速（百分比）。
 *
 * 默认：低温<35°C→20%，中温35–45°C→60%，高温>45°C→100%；
 * 防凝露：低于 [condensationTemp] 时降速避免结露。
 */
data class ThermalThresholds(
    val lowTemp: Int = 35,
    val midTemp: Int = 45,
    val highTemp: Int = 50,
    val condensationTemp: Int = 10,
    val lowSpeed: Int = 20,
    val midSpeed: Int = 60,
    val highSpeed: Int = 100,
) {
    /** 依据温度计算目标转速百分比 */
    fun speedFor(tempC: Float): Int = when {
        tempC <= condensationTemp -> lowSpeed.coerceAtMost(20)
        tempC < lowTemp -> lowSpeed
        tempC < midTemp -> midSpeed
        tempC < highTemp -> ((midSpeed + highSpeed) / 2)
        else -> highSpeed
    }
}

class SettingsRepository(context: Context) {

    private val dataStore = context.applicationContext.settingsDataStore

    // —— 主题设置 ——

    /** 动态取色默认关闭，使用应用自带的紫灰主题；用户可在设置中开启。 */
    val dynamicColor: Flow<Boolean> = dataStore.data.map { it[KEY_DYNAMIC_COLOR] ?: false }

    /** 深色模式:system / light / dark */
    val darkMode: Flow<String> = dataStore.data.map { it[KEY_DARK_MODE] ?: "system" }

    val palette: Flow<String> = dataStore.data.map { it[KEY_PALETTE] ?: "tonal_spot" }
    val predictiveBack: Flow<Boolean> = dataStore.data.map { it[KEY_PREDICTIVE_BACK] ?: true }

    /** 界面语言:system / zh / en */
    val appLanguage: Flow<String> = dataStore.data.map { it[KEY_APP_LANGUAGE] ?: "system" }

    suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { it[KEY_DYNAMIC_COLOR] = enabled }
    }

    suspend fun setDarkMode(mode: String) {
        dataStore.edit { it[KEY_DARK_MODE] = mode }
    }

    suspend fun setPalette(palette: String) {
        dataStore.edit { it[KEY_PALETTE] = palette }
    }

    suspend fun setPredictiveBack(enabled: Boolean) {
        dataStore.edit { it[KEY_PREDICTIVE_BACK] = enabled }
    }

    suspend fun setAppLanguage(language: String) {
        dataStore.edit { it[KEY_APP_LANGUAGE] = language }
    }

    // —— 温控阈值 ——

    val thresholds: Flow<ThermalThresholds> = dataStore.data.map { prefs ->
        ThermalThresholds(
            lowTemp = prefs[KEY_LOW_TEMP] ?: 35,
            midTemp = prefs[KEY_MID_TEMP] ?: 45,
            highTemp = prefs[KEY_HIGH_TEMP] ?: 50,
            condensationTemp = prefs[KEY_CONDENSATION] ?: 10,
            lowSpeed = prefs[KEY_LOW_SPEED] ?: 20,
            midSpeed = prefs[KEY_MID_SPEED] ?: 60,
            highSpeed = prefs[KEY_HIGH_SPEED] ?: 100,
        )
    }

    suspend fun update(thresholds: ThermalThresholds) {
        dataStore.edit { prefs ->
            prefs[KEY_LOW_TEMP] = thresholds.lowTemp
            prefs[KEY_MID_TEMP] = thresholds.midTemp
            prefs[KEY_HIGH_TEMP] = thresholds.highTemp
            prefs[KEY_CONDENSATION] = thresholds.condensationTemp
            prefs[KEY_LOW_SPEED] = thresholds.lowSpeed
            prefs[KEY_MID_SPEED] = thresholds.midSpeed
            prefs[KEY_HIGH_SPEED] = thresholds.highSpeed
        }
    }

    companion object {
        private val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        private val KEY_DARK_MODE = stringPreferencesKey("dark_mode")
        private val KEY_PALETTE = stringPreferencesKey("palette")
        private val KEY_PREDICTIVE_BACK = booleanPreferencesKey("predictive_back")
        private val KEY_APP_LANGUAGE = stringPreferencesKey("app_language")
        private val KEY_LOW_TEMP = intPreferencesKey("low_temp")
        private val KEY_MID_TEMP = intPreferencesKey("mid_temp")
        private val KEY_HIGH_TEMP = intPreferencesKey("high_temp")
        private val KEY_CONDENSATION = intPreferencesKey("condensation_temp")
        private val KEY_LOW_SPEED = intPreferencesKey("low_speed")
        private val KEY_MID_SPEED = intPreferencesKey("mid_speed")
        private val KEY_HIGH_SPEED = intPreferencesKey("high_speed")
    }
}
