package io.github.wxmyyds.coldfront.data

import androidx.datastore.preferences.core.mutablePreferencesOf
import io.github.wxmyyds.coldfront.domain.ConnectionState
import io.github.wxmyyds.coldfront.domain.CoolerDeviceType
import io.github.wxmyyds.coldfront.domain.CoolerLiveState
import io.github.wxmyyds.coldfront.domain.FanMode
import io.github.wxmyyds.coldfront.domain.LightEffect
import io.github.wxmyyds.coldfront.domain.RGBConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileJsonTest {
    // Android's org.json API lacks JSONObject/JSONArray.similar; compare structures using
    // only platform APIs even though the JVM runner uses the real org.json implementation.
    private fun Any?.jsonValue(): Any? = when (this) {
        null, JSONObject.NULL -> null
        is JSONObject -> keys().asSequence().associateWith { get(it).jsonValue() }
        is JSONArray -> (0 until length()).map { get(it).jsonValue() }
        is Number -> toDouble()
        else -> this
    }

    private fun Any.similar(other: Any?): Boolean = jsonValue() == other.jsonValue()
    private fun row(id: String, address: String = TEST_MAC): JSONObject = JSONObject()
        .put("id", id)
        .put("name", "Custom name")
        .put("deviceType", "JACKET_8_PRO")
        .put("macAddress", address)
        .put("createdAtMs", 100L)
        .put("lastConnectedAtMs", 200L)
        .put("fanPercent", 65)
        .put("fanMode", "AUTO")

    @Test
    fun `valid invalid valid rows decode independently and preserve every raw entry`() {
        val malformed = row("broken").put("macAddress", "invalid address")
        val missingId = row("missing-id").apply { remove("id") }
        val input = JSONArray()
            .put(row("first"))
            .put(malformed)
            .put("not a profile")
            .put(JSONObject.NULL)
            .put(missingId)
            .put(row("last", OTHER_MAC))
        val document = ProfileJson.parse(input.toString())
        assertEquals(listOf("first", "last"), document.profiles.map { it.id })
        assertEquals(input.length(), document.entries.size)
        assertTrue(input.similar(JSONArray(document.toJson())))
    }

    @Test
    fun `legacy missing optional fields have stable defaults without inventing model migrations`() {
        val minimal = JSONObject()
            .put("id", "legacy")
            .put("deviceType", "JACKET_5")
            .put("macAddress", "aa:bb:cc:dd:ee:ff")
        val json = JSONArray().put(minimal).toString()
        val parsed = ProfileJson.parse(json).profiles.single()
        assertEquals("legacy", parsed.id)
        assertEquals(CoolerDeviceType.JACKET_5.deviceName, parsed.name)
        assertEquals(TEST_MAC, parsed.macAddress)
        assertEquals(0L, parsed.createdAtMs)
        assertEquals(0L, parsed.lastConnectedAtMs)
        assertEquals(50, parsed.fanPercent)
        assertEquals(FanMode.MANUAL, parsed.fanMode)
        assertNull(parsed.rgb)
        assertEquals(parsed, ProfileJson.parse(json).profiles.single())
        assertTrue(JSONArray(json).similar(JSONArray(ProfileJson.parse(json).toJson())))
    }

    @Test
    fun `invalid optional RGB does not discard profile or alter raw RGB`() {
        val invalidRgb = listOf(
            JSONObject().put("effect", "ALWAYS_BRIGHT").put("red", 1).put("green", 2),
            JSONObject().put("effect", "ALWAYS_BRIGHT").put("red", 256).put("green", 2).put("blue", 3),
            JSONObject().put("effect", "ALWAYS_BRIGHT").put("red", -1).put("green", 2).put("blue", 3),
            JSONObject().put("effect", "ALWAYS_BRIGHT").put("red", 1.5).put("green", 2).put("blue", 3),
            JSONObject().put("effect", "ALWAYS_BRIGHT").put("red", "bad").put("green", 2).put("blue", 3),
            JSONObject().put("effect", "FUTURE_EFFECT").put("red", 1).put("green", 2).put("blue", 3),
            "not an object",
        )
        invalidRgb.forEach { rgb ->
            val input = JSONArray().put(row("rgb").put("rgb", rgb))
            val document = ProfileJson.parse(input.toString())
            assertEquals("rgb", document.profiles.single().id)
            assertNull(document.profiles.single().rgb)
            assertTrue(input.similar(JSONArray(document.toJson())))
        }
    }

    @Test
    fun `serializer preserves existing JSON field names types and complete RGB`() {
        val original = profile().copy(rgb = RGBConfig(LightEffect.BREATH_SINGLE, 10, 20, 30))
        val json = ProfileJson.Document(listOf(ProfileJson.newEntry(original))).toJson()
        assertEquals(original, ProfileJson.parse(json).profiles.single())
        val raw = JSONArray(json).getJSONObject(0)
        assertEquals(setOf(
            "id", "name", "deviceType", "macAddress", "createdAtMs", "lastConnectedAtMs",
            "fanPercent", "fanMode", "rgb",
        ), raw.keys().asSequence().toSet())
        listOf("id", "name", "deviceType", "macAddress", "fanMode").forEach {
            assertTrue(raw.get(it) is String)
        }
        listOf("createdAtMs", "lastConnectedAtMs", "fanPercent").forEach {
            assertTrue(raw.get(it) is Number)
        }
        val rgb = raw.getJSONObject("rgb")
        assertEquals("BREATH_SINGLE", rgb.getString("effect"))
        assertEquals(10, rgb.getInt("red"))
        assertEquals(20, rgb.getInt("green"))
        assertEquals(30, rgb.getInt("blue"))
    }

    @Test
    fun `unknown models modes and malformed rows survive connection writes and deletion`() = runTest {
        val unknownModel = row("future-model").put("deviceType", "JACKET_99")
            .put("future", JSONObject().put("nested", JSONArray().put(1).put("keep")))
        val unknownMode = row("future-mode").put("fanMode", "FUTURE_MODE")
        val malformed = row("broken").put("id", 17)
        val known = row("known").put("futureFlag", true)
            .put("futureObject", JSONObject().put("token", "keep"))
        val input = JSONArray().put(unknownModel).put(known).put(unknownMode).put(malformed)
            .put(JSONArray().put("opaque")).put(JSONObject.NULL)
        val store = TestPreferencesStore(mutablePreferencesOf(profilesKey to input.toString()))
        val repository = ProfileRepository(store)
        assertEquals(listOf("known"), repository.profiles.first().map { it.id })
        repository.recordConnection(CoolerLiveState(
            connection = ConnectionState.CONNECTED,
            deviceType = CoolerDeviceType.JACKET_6_PRO,
            deviceAddress = TEST_MAC,
            deviceName = "Ignored rename",
        ))
        val afterRecord = JSONArray(store.snapshot[profilesKey])
        assertTrue(unknownModel.similar(afterRecord.getJSONObject(0)))
        assertTrue(unknownMode.similar(afterRecord.getJSONObject(2)))
        assertTrue(malformed.similar(afterRecord.getJSONObject(3)))
        assertTrue(afterRecord.getJSONObject(1).getBoolean("futureFlag"))
        assertEquals("keep", afterRecord.getJSONObject(1).getJSONObject("futureObject").getString("token"))
        assertEquals("Custom name", afterRecord.getJSONObject(1).getString("name"))
        assertEquals("JACKET_6_PRO", afterRecord.getJSONObject(1).getString("deviceType"))
        repository.delete("known")
        val expected = JSONArray().put(unknownModel).put(unknownMode).put(malformed)
            .put(JSONArray().put("opaque")).put(JSONObject.NULL)
        assertTrue(expected.similar(JSONArray(store.snapshot[profilesKey])))
        assertTrue(repository.profiles.first().isEmpty())
        // Even an explicit ID that happens to belong to a hidden future row must not destroy it.
        repository.delete("future-model")
        assertTrue(expected.similar(JSONArray(store.snapshot[profilesKey])))
    }

    @Test
    fun `recording known profile preserves malformed optional RGB and legacy omissions`() = runTest {
        val badRgb = JSONObject().put("effect", "FUTURE_EFFECT").put("futureColor", "keep")
        val raw = row("known").put("rgb", badRgb).apply {
            remove("createdAtMs")
            remove("fanMode")
        }
        val store = TestPreferencesStore(mutablePreferencesOf(
            profilesKey to JSONArray().put(raw).toString(),
        ))
        val repository = ProfileRepository(store)
        val saved = repository.recordConnection(CoolerLiveState(
            connection = ConnectionState.CONNECTED,
            deviceType = CoolerDeviceType.JACKET_8_PRO,
            deviceAddress = TEST_MAC,
        ))!!
        assertEquals(0L, saved.createdAtMs)
        assertEquals(FanMode.MANUAL, saved.fanMode)
        assertNull(saved.rgb)
        val updated = JSONArray(store.snapshot[profilesKey]).getJSONObject(0)
        assertTrue(badRgb.similar(updated.getJSONObject("rgb")))
        assertTrue(!updated.has("createdAtMs"))
        assertTrue(!updated.has("fanMode"))
    }
}
