package app.aaps.core.nssdk.mapper

import com.google.common.truth.Truth.assertThat
import com.google.gson.JsonSyntaxException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Device status carries **schema-less** JSON: `pump.extended`, `openaps.suggested`, `openaps.enacted`
 * and `openaps.iob` hold whatever the pump driver and the APS algorithm put there. AAPS does not
 * know their shape, it only has to carry them through without losing anything.
 *
 * Today the wire model [app.aaps.core.nssdk.remotemodel.RemoteDeviceStatus] holds those subtrees as
 * **Gson** `JsonObject`, while the local model
 * [app.aaps.core.nssdk.localmodel.devicestatus.NSDeviceStatus] already holds them as **kotlinx**
 * `JsonObject`. `DeviceStatusMapper` bridges the two by turning a tree into text and parsing it
 * again, in both directions.
 *
 * These tests assert on **parsed structure**, never on `toString()`. The existing
 * `DeviceStatusExtensionKtTest` compares string form, which would fail after a move to kotlinx even
 * if every value were preserved, because the two libraries do not print identically.
 *
 * The last test is the important one: since the schema is unknown, a rewrite that quietly drops keys
 * it does not recognise would pass every other assertion here.
 */
class DeviceStatusMapperTest {

    private val deviceStatusJson = """
        {
          "app": "AAPS",
          "identifier": "abc123",
          "device": "AndroidAPS-DanaRS",
          "created_at": "2023-01-02T15:29:39.460Z",
          "date": 1672673379460,
          "uploaderBattery": 88,
          "isCharging": false,
          "pump": {
            "clock": "2023-01-02T15:29:39.460Z",
            "reservoir": 123.4,
            "battery": { "percent": 75 },
            "status": { "status": "normal", "timestamp": "2023-01-02T15:20:20.656Z" },
            "extended": {
              "Version": "3.1.0.3-dev",
              "PumpIOB": 0.692,
              "LastBolus": "2.1.2023 16:20:20",
              "BaseBasalRate": 0.5,
              "ActiveProfile": "MyProfile"
            }
          },
          "openaps": {
            "suggested": {
              "temp": "absolute",
              "bg": 133,
              "tick": -6,
              "eventualBG": 67,
              "insulinReq": 0,
              "sensitivityRatio": 1,
              "variable_sens": 97.5,
              "predBGs": {
                "IOB": [133, 127, 121, 116, 111],
                "ZT": [133, 127, 121, 115, 110],
                "UAM": [133, 127, 121, 115, 110]
              },
              "reason": "COB: 0, Dev: 0.1, BGI: -0.3; minGuardBG 2.1<4.0",
              "COB": 0,
              "IOB": 0.692,
              "duration": 90,
              "rate": 0
            },
            "enacted": { "temp": "absolute", "duration": 90, "rate": 0, "received": true },
            "iob": { "iob": 0.692, "basaliob": -0.411, "activity": 0.0126 }
          }
        }
    """.trimIndent()

    // ------------------------------------------------------------------ parsing

    @Test
    fun `parses the flat fields`() {
        val status = deviceStatusJson.toNSDeviceStatus()
        assertThat(status.app).isEqualTo("AAPS")
        assertThat(status.device).isEqualTo("AndroidAPS-DanaRS")
        assertThat(status.uploaderBattery).isEqualTo(88)
        assertThat(status.pump?.battery?.percent).isEqualTo(75)
        assertThat(status.pump?.status?.status).isEqualTo("normal")
    }

    @Test
    fun `keeps the schema-less pump extended subtree`() {
        val extended = deviceStatusJson.toNSDeviceStatus().pump?.extended
        assertThat(extended).isNotNull()
        assertThat(extended!!.keys).containsExactly(
            "Version", "PumpIOB", "LastBolus", "BaseBasalRate", "ActiveProfile"
        )
        assertThat(extended["Version"]?.jsonPrimitive?.content).isEqualTo("3.1.0.3-dev")
        assertThat(extended["PumpIOB"]?.jsonPrimitive?.content).isEqualTo("0.692")
    }

    @Test
    fun `keeps nesting three levels deep`() {
        val suggested = deviceStatusJson.toNSDeviceStatus().openaps?.suggested
        assertThat(suggested).isNotNull()

        val predBGs = suggested!!["predBGs"]?.jsonObject
        assertThat(predBGs).isNotNull()
        assertThat(predBGs!!.keys).containsExactly("IOB", "ZT", "UAM")

        val iobCurve = predBGs["IOB"]!!.jsonArray
        assertThat(iobCurve).hasSize(5)
        assertThat(iobCurve[0].jsonPrimitive.content).isEqualTo("133")
        assertThat(iobCurve[4].jsonPrimitive.content).isEqualTo("111")
    }

    @Test
    fun `keeps numbers exactly as sent`() {
        val suggested = deviceStatusJson.toNSDeviceStatus().openaps?.suggested!!
        // A whole number must not gain a decimal point, and a decimal must not lose precision.
        assertThat(suggested["bg"]?.jsonPrimitive?.content).isEqualTo("133")
        assertThat(suggested["COB"]?.jsonPrimitive?.content).isEqualTo("0")
        assertThat(suggested["tick"]?.jsonPrimitive?.content).isEqualTo("-6")
        assertThat(suggested["variable_sens"]?.jsonPrimitive?.content).isEqualTo("97.5")
        assertThat(suggested["IOB"]?.jsonPrimitive?.content).isEqualTo("0.692")
        assertThat(deviceStatusJson.toNSDeviceStatus().openaps?.iob?.get("basaliob")?.jsonPrimitive?.content)
            .isEqualTo("-0.411")
    }

    @Test
    fun `keeps a long reason string with punctuation`() {
        val suggested = deviceStatusJson.toNSDeviceStatus().openaps?.suggested!!
        assertThat(suggested["reason"]?.jsonPrimitive?.content)
            .isEqualTo("COB: 0, Dev: 0.1, BGI: -0.3; minGuardBG 2.1<4.0")
    }

    // ------------------------------------------------------------------ round trip

    @Test
    fun `subtrees survive a round trip through the wire format`() {
        val first = deviceStatusJson.toNSDeviceStatus()
        val second = first.convertToRemoteAndBack()

        assertSameTree(first.pump?.extended, second.pump?.extended)
        assertSameTree(first.openaps?.suggested, second.openaps?.suggested)
        assertSameTree(first.openaps?.enacted, second.openaps?.enacted)
        assertSameTree(first.openaps?.iob, second.openaps?.iob)

        // and the nesting specifically
        val before = first.openaps?.suggested!!["predBGs"]!!.jsonObject["IOB"]!!.jsonArray
        val after = second.openaps?.suggested!!["predBGs"]!!.jsonObject["IOB"]!!.jsonArray
        assertThat(after).hasSize(before.size)
        assertThat(after[0].jsonPrimitive.content).isEqualTo(before[0].jsonPrimitive.content)
    }

    @Test
    fun `missing subtrees stay null`() {
        val json = """{"app":"AAPS","device":"d","date":1672673379460}"""
        val status = json.toNSDeviceStatus()
        assertThat(status.pump).isNull()
        assertThat(status.openaps).isNull()
    }

    @Test
    fun `an empty subtree stays empty and does not become null`() {
        val json = """{"app":"AAPS","date":1,"openaps":{"suggested":{}}}"""
        val openaps = json.toNSDeviceStatus().openaps
        assertThat(openaps?.suggested).isNotNull()
        assertThat(openaps?.suggested?.keys).isEmpty()
        assertThat(openaps?.enacted).isNull()   // absent key -> null
    }

    /**
     * An **explicit** `null` is not the same as an absent key here, and it throws.
     *
     * The field is declared `JsonObject?`, but Gson's adapter for the concrete `JsonObject` type
     * rejects `JsonNull` instead of mapping it to Kotlin `null`. An absent key is fine, a written
     * `null` is not.
     *
     * Pinned, not fixed - the caller `NSClientV3Service.onDataCreateUpdate` is a socket.io listener
     * with no try/catch, so this escapes onto the socket callback thread. Worth noting that AAPS
     * itself never writes `null` here, but nothing stops another uploader from doing so.
     *
     * kotlinx.serialization would accept it and give `null`, so a migration would **change** this.
     * If that is wanted, change this test on purpose.
     */
    @Test
    fun `an explicit null subtree throws - current behaviour, pinned`() {
        val json = """{"app":"AAPS","date":1,"openaps":{"suggested":{},"enacted":null}}"""
        assertThrows<JsonSyntaxException> { json.toNSDeviceStatus() }
    }

    /**
     * The point of holding these as raw JSON is that AAPS does not know the schema. A future
     * Nightscout, a new pump driver or a newer AAPS may add keys this version has never seen, and
     * they must still be carried through untouched.
     */
    @Test
    fun `unknown keys from a newer sender are preserved`() {
        val json = """
            {"app":"AAPS","date":1,
             "openaps":{"suggested":{
               "bg":133,
               "somethingFromTheFuture":"keep me",
               "nestedFuture":{"deep":{"deeper":[1,2,3]}}
             }}}
        """.trimIndent()

        val suggested = json.toNSDeviceStatus().openaps?.suggested!!
        assertThat(suggested["somethingFromTheFuture"]?.jsonPrimitive?.content).isEqualTo("keep me")
        assertThat(suggested["nestedFuture"]?.jsonObject?.get("deep")?.jsonObject?.get("deeper")?.jsonArray)
            .hasSize(3)

        // and they must still be there after a trip through the wire model
        val back = json.toNSDeviceStatus().convertToRemoteAndBack().openaps?.suggested!!
        assertThat(back["somethingFromTheFuture"]?.jsonPrimitive?.content).isEqualTo("keep me")
        assertThat(back["nestedFuture"]?.jsonObject?.get("deep")?.jsonObject?.get("deeper")?.jsonArray)
            .hasSize(3)
    }

    /** Compare two subtrees by content, never by printed form. */
    private fun assertSameTree(expected: JsonObject?, actual: JsonObject?) {
        if (expected == null) {
            assertThat(actual).isNull()
            return
        }
        assertThat(actual).isNotNull()
        assertThat(actual!!.keys).isEqualTo(expected.keys)
        for (key in expected.keys) assertThat(actual[key]).isEqualTo(expected[key])
    }
}
