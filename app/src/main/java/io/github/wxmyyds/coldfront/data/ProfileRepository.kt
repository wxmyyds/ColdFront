package io.github.wxmyyds.coldfront.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.wxmyyds.coldfront.domain.CoolerLiveState
import io.github.wxmyyds.coldfront.domain.CoolerProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import java.io.IOException

private val Context.profileDataStore by preferencesDataStore(name = "cooler_profiles")

/** Profile mutations and their references are committed in one DataStore transaction. */
class ProfileRepository internal constructor(private val dataStore: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.profileDataStore)

    /** Strict reads still fail on corrupt data; observers opt into per-snapshot recovery below. */
    val profiles: Flow<List<CoolerProfile>> = observeProfiles { throw it }

    /** Explicit service intent only: old active profiles never implicitly enable auto service. */
    val serviceProfile: Flow<CoolerProfile?> = observeServiceProfile { throw it }

    /** Startup default only: the most recently connected profile never becomes an implicit target. */
    val defaultProfile: Flow<CoolerProfile?> = observeDefaultProfile { throw it }

    /** Retain the last good list on decode failure and wait for the next DataStore snapshot. */
    fun observeProfiles(reportError: (IOException) -> Unit): Flow<List<CoolerProfile>> =
        dataStore.data.decodeStorageSnapshots(reportError) { prefs ->
            ProfileJson.parse(prefs[KEY_PROFILES]).profiles
        }.distinctUntilChanged()

    /** Decode failures are not OFF; explicitly clearing the reference still emits null at once. */
    fun observeServiceProfile(reportError: (IOException) -> Unit): Flow<CoolerProfile?> =
        dataStore.data.decodeStorageSnapshots(reportError) { prefs ->
            referencedProfile(prefs, KEY_SERVICE)
        }.distinctUntilChanged()

    /** Missing references are independent of the profile document, including a corrupt document. */
    fun observeDefaultProfile(reportError: (IOException) -> Unit): Flow<CoolerProfile?> =
        dataStore.data.decodeStorageSnapshots(reportError) { prefs ->
            referencedProfile(prefs, KEY_DEFAULT)
        }.distinctUntilChanged()

    /**
     * Call once per successful connection session, regardless of normal/raw/saved connection path.
     * Invalid or not-yet-connected states do not access storage. Existing user settings survive;
     * active identity wins a duplicate merge, otherwise the oldest known identity is retained.
     */
    suspend fun recordConnection(state: CoolerLiveState): CoolerProfile? {
        if (!state.isConnected) return null
        val type = state.deviceType ?: return null
        val address = normalizeMacAddress(state.deviceAddress) ?: return null
        var recorded: CoolerProfile? = null
        dataStore.edit { prefs ->
            val document = ProfileJson.parse(prefs[KEY_PROFILES])
            val matches = document.entries.filter { it.profile?.macAddress == address }
            val retained = matches.firstOrNull { it.profile?.id == prefs[KEY_ACTIVE] }
                ?: matches.minByOrNull { it.profile!!.createdAtMs }
            val now = System.currentTimeMillis()
            val profile = retained?.profile?.copy(
                deviceType = type,
                macAddress = address,
                lastConnectedAtMs = now,
            ) ?: CoolerProfile(
                name = state.deviceName?.takeIf { it.isNotBlank() } ?: type.deviceName,
                deviceType = type,
                macAddress = address,
                createdAtMs = now,
                lastConnectedAtMs = now,
            )
            val entries = buildList {
                document.entries.forEach { entry ->
                    when {
                        entry === retained -> add(ProfileJson.connectedEntry(entry, profile))
                        entry.profile?.macAddress != address -> add(entry)
                        // Only decoded, same-MAC duplicates are removed. Opaque rows survive.
                    }
                }
                if (retained == null) add(ProfileJson.newEntry(profile))
            }
            prefs[KEY_PROFILES] = ProfileJson.Document(entries).toJson()
            prefs[KEY_ACTIVE] = profile.id
            // An already opted-in duplicate refers to this same device; retain that intent.
            val serviceId = prefs[KEY_SERVICE]
            if (serviceId != null && matches.any { it.profile?.id == serviceId }) {
                prefs[KEY_SERVICE] = profile.id
            }
            val defaultId = prefs[KEY_DEFAULT]
            if (defaultId != null && matches.any { it.profile?.id == defaultId }) {
                prefs[KEY_DEFAULT] = profile.id
            }
            recorded = profile
        }
        return recorded
    }

    suspend fun delete(id: String): CoolerProfile? {
        var removed: CoolerProfile? = null
        dataStore.edit { prefs ->
            val document = ProfileJson.parse(prefs[KEY_PROFILES])
            removed = document.profiles.firstOrNull { it.id == id }
            prefs[KEY_PROFILES] = ProfileJson.Document(
                document.entries.filterNot { it.profile?.id == id },
            ).toJson()
            if (prefs[KEY_ACTIVE] == id) prefs.remove(KEY_ACTIVE)
            if (prefs[KEY_SERVICE] == id) prefs.remove(KEY_SERVICE)
            if (prefs[KEY_DEFAULT] == id) prefs.remove(KEY_DEFAULT)
        }
        return removed
    }

    /** Read both the selected ID and the profile array from exactly one preferences snapshot. */
    suspend fun loadActiveProfile(): CoolerProfile? = referencedProfile(dataStore.data.first(), KEY_ACTIVE)

    /** Missing, deleted or unsupported service targets are OFF, not an active-profile fallback. */
    suspend fun loadServiceProfile(): CoolerProfile? = referencedProfile(dataStore.data.first(), KEY_SERVICE)

    /** Missing, deleted or unsupported startup targets are OFF, not a last-connected fallback. */
    suspend fun loadDefaultProfile(): CoolerProfile? = referencedProfile(dataStore.data.first(), KEY_DEFAULT)

    /**
     * Validate and persist the startup default atomically. Disabling remains possible even if the
     * profile JSON is corrupt: clearing this independent key never overwrites that document.
     */
    suspend fun setDefaultProfile(id: String?) {
        dataStore.edit { prefs ->
            if (id == null) {
                prefs.remove(KEY_DEFAULT)
            } else {
                val profiles = ProfileJson.parse(prefs[KEY_PROFILES]).profiles
                if (profiles.none { it.id == id }) {
                    throw IOException("Default profile does not exist or is unsupported: $id")
                }
                prefs[KEY_DEFAULT] = id
            }
        }
    }

    /**
     * Validate and persist desired service intent atomically. Disabling remains possible even if
     * the profile JSON is corrupt: clearing this independent key never overwrites that document.
     */
    suspend fun setServiceProfile(id: String?) {
        dataStore.edit { prefs ->
            if (id == null) {
                prefs.remove(KEY_SERVICE)
            } else {
                val profiles = ProfileJson.parse(prefs[KEY_PROFILES]).profiles
                if (profiles.none { it.id == id }) {
                    throw IOException("Service profile does not exist or is unsupported: $id")
                }
                prefs[KEY_SERVICE] = id
            }
        }
    }

    private fun referencedProfile(prefs: Preferences, key: Preferences.Key<String>): CoolerProfile? {
        val id = prefs[key] ?: return null
        val profiles = ProfileJson.parse(prefs[KEY_PROFILES]).profiles
        return profiles.firstOrNull { it.id == id }
    }

    companion object {
        private val KEY_PROFILES = stringPreferencesKey("profiles_json")
        private val KEY_ACTIVE = stringPreferencesKey("active_profile_id")
        private val KEY_SERVICE = stringPreferencesKey("service_profile_id")
        private val KEY_DEFAULT = stringPreferencesKey("default_profile_id")
    }
}
