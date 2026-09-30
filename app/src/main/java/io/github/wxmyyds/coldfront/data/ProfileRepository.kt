package io.github.wxmyyds.coldfront.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.wxmyyds.coldfront.domain.CoolerProfile
import io.github.wxmyyds.coldfront.domain.ProfileJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.profileDataStore by preferencesDataStore(name = "cooler_profiles")

/**
 * 设备档案持久化（DataStore）。存为一个 JSON 字符串键。
 */
class ProfileRepository(context: Context) {

    private val dataStore = context.applicationContext.profileDataStore

    val profiles: Flow<List<CoolerProfile>> = dataStore.data.map { prefs ->
        ProfileJson.fromJson(prefs[KEY_PROFILES].orEmpty())
    }

    val activeProfileId: Flow<String?> = dataStore.data.map { it[KEY_ACTIVE] }

    suspend fun upsert(profile: CoolerProfile) {
        dataStore.edit { prefs ->
            val list = ProfileJson.fromJson(prefs[KEY_PROFILES].orEmpty()).toMutableList()
            val idx = list.indexOfFirst { it.id == profile.id }
            if (idx >= 0) list[idx] = profile else list.add(profile)
            prefs[KEY_PROFILES] = ProfileJson.toJson(list)
        }
    }

    suspend fun delete(id: String) {
        dataStore.edit { prefs ->
            val list = ProfileJson.fromJson(prefs[KEY_PROFILES].orEmpty()).toMutableList()
            list.removeAll { it.id == id }
            prefs[KEY_PROFILES] = ProfileJson.toJson(list)
            if (prefs[KEY_ACTIVE] == id) prefs.remove(KEY_ACTIVE)
        }
    }

    suspend fun setActive(id: String?) {
        dataStore.edit { prefs ->
            if (id == null) prefs.remove(KEY_ACTIVE) else prefs[KEY_ACTIVE] = id
        }
    }

    /** 读取当前激活的档案（供开机自启、磁贴等场景） */
    suspend fun loadActiveProfile(): CoolerProfile? {
        val all = profiles.first()
        val activeId = activeProfileId.first() ?: return null
        return all.firstOrNull { it.id == activeId }
    }

    companion object {
        private val KEY_PROFILES = stringPreferencesKey("profiles_json")
        private val KEY_ACTIVE = stringPreferencesKey("active_profile_id")
    }
}
