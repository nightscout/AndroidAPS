package app.aaps.core.nssdk.mapper

import app.aaps.core.nssdk.localmodel.treatment.NSBolus
import app.aaps.core.nssdk.localmodel.treatment.NSCarbs
import app.aaps.core.nssdk.localmodel.treatment.NSTemporaryBasal
import app.aaps.core.nssdk.localmodel.treatment.NSTemporaryTarget
import com.google.common.truth.Truth.assertThat
import com.google.gson.JsonSyntaxException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Parsing tests built from real payloads taken from a Nightscout test instance
 * (`/api/v1/treatments.json`, AAPS uploader, Dana RS pump).
 *
 * Two jobs:
 *
 * 1. Pin what the current parser produces for well formed records. The wire layer is about to be
 *    rewritten (Gson to kotlinx.serialization, Retrofit to Ktor) and the output has to stay the
 *    same. These are characterization tests: if one fails after the rewrite, the rewrite is wrong,
 *    not the test.
 * 2. Show that a record with fields missing does not crash. Nightscout data comes from many
 *    uploaders and many app versions, so almost every field can be absent.
 *
 * The payloads are real but the identifying parts were replaced: `pumpSerial` and `subject`.
 */
class RealNightscoutTreatmentTest {

    // ---------------------------------------------------------------- complete records

    @Test
    fun `parses a real SMB correction bolus`() {
        val json = """
            {"_id":"6a7413f54d1ba8c2e419e30e","app":"AAPS","date":1785992179555,
             "eventType":"Correction Bolus",
             "icfg":{"concentration":1,"insulinEndTime":32040000,"insulinLabel":"Novorapid (1) 75m 8.9h U100","insulinPeakTime":4500000},
             "insulin":0.25,"isBasalInsulin":false,"isReadOnly":false,"isValid":true,
             "pumpId":1785992181495,"pumpSerial":"TESTSERIAL","pumpType":"DANA_RS","type":"SMB","utcOffset":120,
             "created_at":"2026-08-06T04:56:19.555Z","identifier":"1bebd84b-8d61-58b2-a967-1b34a1e8c4d4",
             "srvModified":1785992181588,"srvCreated":1785992181588,"subject":"tester",
             "mills":1785992179555,"carbs":null}
        """.trimIndent()

        val treatment = json.toNSTreatment()
        assertThat(treatment).isInstanceOf(NSBolus::class.java)
        treatment as NSBolus
        assertThat(treatment.insulin).isEqualTo(0.25)
        assertThat(treatment.date).isEqualTo(1785992179555)
        assertThat(treatment.identifier).isEqualTo("1bebd84b-8d61-58b2-a967-1b34a1e8c4d4")
        assertThat(treatment.isValid).isTrue()
    }

    @Test
    fun `parses a real temp basal`() {
        val json = """
            {"_id":"6a740f444d1ba8c2e419e301","absolute":2,"app":"AAPS","date":1785990980390,
             "duration":25,"durationInMilliseconds":1500265,"eventType":"Temp Basal",
             "isReadOnly":false,"isValid":true,"pumpId":1785990980390,"pumpSerial":"TESTSERIAL",
             "pumpType":"DANA_RS","rate":2,"type":"NORMAL","utcOffset":120,
             "created_at":"2026-08-06T04:36:20.390Z","identifier":"cb180766-42ed-55cc-a253-5200d4e0c2c6",
             "srvModified":1785992481019,"srvCreated":1785990980561,"subject":"tester",
             "endId":1785992480655,"modifiedBy":"tester","endmills":1785992480655,
             "carbs":null,"insulin":null}
        """.trimIndent()

        val treatment = json.toNSTreatment()
        assertThat(treatment).isInstanceOf(NSTemporaryBasal::class.java)
        treatment as NSTemporaryBasal
        assertThat(treatment.date).isEqualTo(1785990980390)
        assertThat(treatment.duration).isEqualTo(1500265)
        assertThat(treatment.isValid).isTrue()
    }

    @Test
    fun `parses a real temporary target`() {
        val json = """
            {"_id":"6a741d764d1ba8c2e419e322","app":"AAPS","date":1785994614220,
             "duration":60,"durationInMilliseconds":3600000,"eventType":"Temporary Target",
             "isReadOnly":false,"isValid":true,"reason":"Automation",
             "targetBottom":144.12472,"targetTop":144.12472,"units":"mg/dl","utcOffset":120,
             "created_at":"2026-08-06T05:36:54.220Z","identifier":"0426fdb9-a59b-59c5-a556-31f08d5382d0",
             "srvModified":1785994614241,"srvCreated":1785994614241,"subject":"tester",
             "mills":1785994614220,"endmills":1785998214220,"carbs":null,"insulin":null}
        """.trimIndent()

        val treatment = json.toNSTreatment()
        assertThat(treatment).isInstanceOf(NSTemporaryTarget::class.java)
        treatment as NSTemporaryTarget
        assertThat(treatment.targetBottom).isEqualTo(144.12472)
        assertThat(treatment.targetTop).isEqualTo(144.12472)
        assertThat(treatment.date).isEqualTo(1785994614220)
    }

    @Test
    fun `parses a real carb correction with notes`() {
        val json = """
            {"_id":"6a74011c4d1ba8c2e419e2de","app":"AAPS","carbs":4,"date":1785987360000,
             "eventType":"Carb Correction","isReadOnly":false,"isValid":true,"notes":"Random carbs",
             "utcOffset":120,"created_at":"2026-08-06T03:36:00.000Z",
             "identifier":"752f76ab-86af-5de0-a5af-e488f8459a91",
             "srvModified":1785987356851,"srvCreated":1785987356851,"subject":"tester",
             "mills":1785987360000,"insulin":null}
        """.trimIndent()

        val treatment = json.toNSTreatment()
        assertThat(treatment).isInstanceOf(NSCarbs::class.java)
        treatment as NSCarbs
        assertThat(treatment.carbs).isEqualTo(4.0)
        assertThat(treatment.notes).isEqualTo("Random carbs")
    }

    // ---------------------------------------------------------------- round trip

    @Test
    fun `bolus survives a round trip to the wire format and back`() {
        val json = """
            {"eventType":"Correction Bolus","date":1785992179555,"insulin":0.25,"type":"SMB",
             "isValid":true,"utcOffset":120,"identifier":"abc","app":"AAPS"}
        """.trimIndent()

        val first = json.toNSTreatment()
        assertThat(first).isNotNull()
        val second = first!!.convertToRemoteAndBack()
        assertThat(second).isEqualTo(first)
    }

    // ---------------------------------------------------------------- missing fields must not crash

    /**
     * Nightscout records come from many uploaders and many app versions. Anything can be absent.
     * None of these may throw - either a treatment comes back, or null, but never an exception.
     */
    @Test
    fun `records with missing fields do not crash the parser`() {
        val cases = listOf(
            "empty object" to "{}",
            "only eventType" to """{"eventType":"Correction Bolus"}""",
            "bolus without insulin" to """{"eventType":"Correction Bolus","date":1785992179555,"isValid":true}""",
            "bolus without date" to """{"eventType":"Correction Bolus","insulin":0.25,"isValid":true}""",
            "temp basal without duration" to """{"eventType":"Temp Basal","date":1785990980390,"rate":2,"isValid":true}""",
            "temp basal without rate" to """{"eventType":"Temp Basal","date":1785990980390,"duration":25,"isValid":true}""",
            "carbs without amount" to """{"eventType":"Carb Correction","date":1785987360000,"isValid":true}""",
            "target without values" to """{"eventType":"Temporary Target","date":1785994614220,"duration":60,"isValid":true}""",
            "unknown eventType" to """{"eventType":"Something New","date":1785992179555,"isValid":true}""",
            "no eventType at all" to """{"date":1785992179555,"insulin":0.25,"isValid":true}""",
            "explicit nulls" to """{"eventType":"Correction Bolus","date":1785992179555,"insulin":null,"carbs":null,"notes":null,"isValid":true}""",
            "no utcOffset" to """{"eventType":"Correction Bolus","date":1785992179555,"insulin":0.25,"isValid":true}""",
            "no identifier" to """{"eventType":"Correction Bolus","date":1785992179555,"insulin":0.25,"isValid":true}"""
        )

        for ((name, json) in cases)
            try {
                json.toNSTreatment() // may return null, must not throw
            } catch (e: Exception) {
                throw AssertionError("Parsing '$name' threw ${e::class.simpleName}: ${e.message}", e)
            }
    }

    /**
     * Malformed text **throws** today - `toNSTreatment()` is
     * `Gson().fromJson(this, RemoteTreatment::class.java).toTreatment()` with no guard, even though
     * its return type is nullable. This test records that, it does not endorse it.
     *
     * Pinned because the caller is unprotected: `NSClientV3Service.onDataCreateUpdate` is a
     * socket.io listener with no try/catch, so whatever this throws escapes onto the socket
     * callback thread. After the move to kotlinx.serialization the exception type would become
     * `SerializationException`, and any `catch` further up would silently stop catching it.
     *
     * If the parser is ever made defensive, change this test on purpose - do not let a rewrite
     * change it by accident.
     */
    @Test
    fun `malformed json throws - current behaviour, pinned`() {
        assertThrows<JsonSyntaxException> { "hello".toNSTreatment() }
        assertThrows<JsonSyntaxException> { """{"eventType":"Correction Bolus","date":""".toNSTreatment() }
        assertThrows<JsonSyntaxException> { """[{"eventType":"Correction Bolus"}]""".toNSTreatment() }
        assertThrows<JsonSyntaxException> { """{"eventType":"Correction Bolus","date":"not-a-number"}""".toNSTreatment() }
    }

    /** Empty text is a separate case: Gson returns null, and the unguarded `.toTreatment()` then fails. */
    @Test
    fun `empty text throws NullPointerException - current behaviour, pinned`() {
        assertThrows<NullPointerException> { "".toNSTreatment() }
    }
}
