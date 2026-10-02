package io.github.wxmyyds.coldfront.data

import io.github.wxmyyds.coldfront.domain.CoolerDeviceType
import io.github.wxmyyds.coldfront.domain.CoolerProfile
import io.github.wxmyyds.coldfront.domain.FanMode
import io.github.wxmyyds.coldfront.domain.LightEffect
import io.github.wxmyyds.coldfront.domain.RGBConfig
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import java.io.IOException
import java.util.Locale

/**
 * The JSON array is also the preservation envelope: unsupported/malformed rows are hidden from
 * callers, not discarded. Never rebuild an existing document from only its decoded profiles.
 */
internal object ProfileJson {
    data class Entry(val raw: Any, val profile: CoolerProfile?)

    class Document(val entries: List<Entry>) {
        val profiles: List<CoolerProfile> get() = entries.mapNotNull { it.profile }

        fun toJson(): String = JSONArray().also { array ->
            entries.forEach { array.put(it.raw) }
        }.toString()
    }

    /** An absent preference means empty; an empty/invalid stored document is an I/O error. */
    fun parse(json: String?): Document {
        if (json == null) return Document(emptyList())
        val array = try {
            val reader = JSONTokener(json)
            val root = reader.nextValue()
            if (root !is JSONArray || reader.nextClean() != '\u0000') {
                throw IOException("Invalid profiles_json: expected one JSON array")
            }
            root
        } catch (error: JSONException) {
            throw IOException("Invalid profiles_json", error)
        }
        return Document(List(array.length()) { index ->
            val raw = array.get(index)
            Entry(raw, (raw as? JSONObject)?.let(::decodeProfile))
        })
    }

    fun newEntry(profile: CoolerProfile): Entry {
        val raw = JSONObject()
            .put("id", profile.id)
            .put("name", profile.name)
            .put("deviceType", profile.deviceType.name)
            .put("macAddress", profile.macAddress)
            .put("createdAtMs", profile.createdAtMs)
            .put("lastConnectedAtMs", profile.lastConnectedAtMs)
            .put("fanPercent", profile.fanPercent)
            .put("fanMode", profile.fanMode.name)
        profile.rgb?.let { rgb ->
            raw.put("rgb", JSONObject()
                .put("effect", rgb.effect.name)
                .put("red", rgb.red)
                .put("green", rgb.green)
                .put("blue", rgb.blue))
        }
        return Entry(raw, profile)
    }

    /** Only connection metadata changes; retain unknown fields and even unreadable optional RGB. */
    fun connectedEntry(entry: Entry, profile: CoolerProfile): Entry {
        val raw = JSONObject(entry.raw.toString())
            .put("deviceType", profile.deviceType.name)
            .put("macAddress", profile.macAddress)
            .put("lastConnectedAtMs", profile.lastConnectedAtMs)
        return Entry(raw, profile)
    }

    private fun decodeProfile(raw: JSONObject): CoolerProfile? {
        val id = (raw.opt("id") as? String)?.takeIf { it.isNotBlank() } ?: return null
        val typeName = raw.opt("deviceType") as? String ?: return null
        val type = CoolerDeviceType.entries.firstOrNull { it.name == typeName } ?: return null
        val address = normalizeMacAddress(raw.opt("macAddress") as? String) ?: return null
        // A missing legacy mode has a default; an unknown future mode must not be rewritten.
        val mode = if (!raw.has("fanMode") || raw.isNull("fanMode")) {
            FanMode.MANUAL
        } else {
            FanMode.entries.firstOrNull { it.name == raw.opt("fanMode") } ?: return null
        }
        // Stable defaults avoid a different profile on every read of the same legacy document.
        val createdAt = raw.optLong("createdAtMs", 0L)
        return CoolerProfile(
            id = id,
            name = raw.opt("name") as? String ?: type.deviceName,
            deviceType = type,
            macAddress = address,
            createdAtMs = createdAt,
            lastConnectedAtMs = raw.optLong("lastConnectedAtMs", createdAt),
            fanPercent = raw.optInt("fanPercent", 50).takeIf { it in 0..100 } ?: 50,
            fanMode = mode,
            rgb = raw.optJSONObject("rgb")?.let(::decodeRgb),
        )
    }

    /** Invalid optional colors do not hide an otherwise usable profile. */
    private fun decodeRgb(raw: JSONObject): RGBConfig? {
        val effect = LightEffect.entries.firstOrNull { it.name == raw.opt("effect") } ?: return null
        fun channel(key: String): Int? {
            val number = raw.opt(key) as? Number ?: return null
            val value = number.toDouble()
            return value.toInt().takeIf { value == it.toDouble() && it in 0..255 }
        }
        return RGBConfig(
            effect = effect,
            red = channel("red") ?: return null,
            green = channel("green") ?: return null,
            blue = channel("blue") ?: return null,
        )
    }
}

private val MAC_ADDRESS = Regex("(?:[0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}")

/** Android Bluetooth addresses use six colon-separated hex octets. */
internal fun normalizeMacAddress(address: String?): String? = address?.trim()
    ?.takeIf(MAC_ADDRESS::matches)
    ?.uppercase(Locale.ROOT)
