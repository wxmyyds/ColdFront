package io.github.wxmyyds.coldfront.domain

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * 持久化的散热器档案。
 */
data class CoolerProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val deviceType: CoolerDeviceType,
    val macAddress: String,
    val createdAtMs: Long = System.currentTimeMillis(),
    val lastConnectedAtMs: Long = System.currentTimeMillis(),
    val fanPercent: Int = 50,
    val fanMode: FanMode = FanMode.MANUAL,
    val rgb: RGBConfig? = null,
) {
    val displayName: String get() = name.ifBlank { deviceType.deviceName }

    val icon: String get() = deviceType.suggestedIcon

    companion object {
        fun fromDevice(
            device: CoolerDevice,
            name: String? = null,
        ): CoolerProfile = CoolerProfile(
            name = name ?: device.displayName,
            deviceType = device.deviceType,
            macAddress = device.address,
            fanPercent = 50,
            fanMode = FanMode.MANUAL,
        )
    }
}

// —— 手动 JSON 序列化（org.json，无额外依赖，100% Kotlin） ——

internal object ProfileJson {

    fun toJson(profiles: List<CoolerProfile>): String {
        val arr = JSONArray()
        for (p in profiles) {
            val o = JSONObject()
            o.put("id", p.id)
            o.put("name", p.name)
            o.put("deviceType", p.deviceType.name)
            o.put("macAddress", p.macAddress)
            o.put("createdAtMs", p.createdAtMs)
            o.put("lastConnectedAtMs", p.lastConnectedAtMs)
            o.put("fanPercent", p.fanPercent)
            o.put("fanMode", p.fanMode.name)
            p.rgb?.let {
                val r = JSONObject()
                r.put("effect", it.effect.name)
                r.put("red", it.red)
                r.put("green", it.green)
                r.put("blue", it.blue)
                o.put("rgb", r)
            }
            arr.put(o)
        }
        return arr.toString()
    }

    fun fromJson(json: String): List<CoolerProfile> = try {
        val arr = JSONArray(json)
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val type = runCatching { CoolerDeviceType.valueOf(o.getString("deviceType")) }
                    .getOrNull() ?: continue
                val mode = runCatching { FanMode.valueOf(o.getString("fanMode")) }
                    .getOrNull() ?: FanMode.MANUAL
                val rgb = o.optJSONObject("rgb")?.let { r ->
                    val effect = runCatching { LightEffect.valueOf(r.getString("effect")) }
                        .getOrNull() ?: LightEffect.ALWAYS_BRIGHT
                    RGBConfig(effect, r.getInt("red"), r.getInt("green"), r.getInt("blue"))
                }
                add(
                    CoolerProfile(
                        id = o.getString("id"),
                        name = o.optString("name", type.deviceName),
                        deviceType = type,
                        macAddress = o.getString("macAddress"),
                        createdAtMs = o.optLong("createdAtMs", System.currentTimeMillis()),
                        lastConnectedAtMs = o.optLong("lastConnectedAtMs", System.currentTimeMillis()),
                        fanPercent = o.optInt("fanPercent", 50),
                        fanMode = mode,
                        rgb = rgb,
                    )
                )
            }
        }
    } catch (_: Exception) {
        emptyList()
    }
}
