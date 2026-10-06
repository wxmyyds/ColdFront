package io.github.wxmyyds.coldfront.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.wxmyyds.coldfront.domain.CoolerDeviceType
import io.github.wxmyyds.coldfront.domain.CoolerProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Atomic, immutable snapshots without Android Context or filesystem scheduling. */
internal class TestPreferencesStore(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
    private val state = MutableStateFlow(initial.toPreferences())
    private val mutex = Mutex()
    val commits = mutableListOf<Preferences>()
    val snapshot: Preferences get() = state.value
    override val data: Flow<Preferences> = state.asStateFlow()

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
        mutex.withLock {
            val updated = transform(state.value).toPreferences()
            commits += updated
            state.value = updated
            updated
        }
}

internal val profilesKey = stringPreferencesKey("profiles_json")
internal val activeKey = stringPreferencesKey("active_profile_id")
internal val serviceKey = stringPreferencesKey("service_profile_id")
internal val defaultKey = stringPreferencesKey("default_profile_id")
internal const val TEST_MAC = "AA:BB:CC:DD:EE:FF"
internal const val OTHER_MAC = "11:22:33:44:55:66"

internal fun profile(
    id: String = "saved",
    address: String = TEST_MAC,
    createdAt: Long = 10L,
): CoolerProfile = CoolerProfile(
    id = id,
    name = "My cooler",
    deviceType = CoolerDeviceType.JACKET_5,
    macAddress = address,
    createdAtMs = createdAt,
    lastConnectedAtMs = 20L,
)

internal fun profilePreferences(
    vararg profiles: CoolerProfile,
    active: String? = null,
    service: String? = null,
    default: String? = null,
): Preferences = mutablePreferencesOf(
    profilesKey to ProfileJson.Document(profiles.map(ProfileJson::newEntry)).toJson(),
).apply {
    if (active != null) this[activeKey] = active
    if (service != null) this[serviceKey] = service
    if (default != null) this[defaultKey] = default
}.toPreferences()

internal suspend inline fun <reified T : Throwable> expectFailure(action: suspend () -> Unit): T {
    try {
        action()
    } catch (failure: Throwable) {
        if (failure is T) return failure
        throw AssertionError("Expected ${T::class.java.name}, got ${failure::class.java.name}", failure)
    }
    throw AssertionError("Expected ${T::class.java.name}")
}
