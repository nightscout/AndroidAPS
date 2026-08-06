package app.aaps.core.nssdk.mapper

import app.aaps.core.nssdk.localmodel.treatment.NSBolus
import app.aaps.core.nssdk.localmodel.treatment.NSCarbs
import com.google.common.truth.Truth.assertThat
import app.aaps.core.nssdk.nsSdkJson
import kotlinx.serialization.SerializationException
import app.aaps.core.nssdk.remotemodel.RemoteTreatment
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Pins how the current parser reacts when a field arrives with the **wrong JSON type**.
 *
 * This is the part of the Gson to kotlinx.serialization move most likely to break quietly. Gson is
 * permissive: it coerces a number into a `String` field, and a numeric string into a `Long` or
 * `Double` field, without complaint. kotlinx.serialization is strict by default and throws instead.
 *
 * That matters because Nightscout data is not written by one uploader. `RemoteTreatment.created_at`
 * is declared `String?` and its own comment in the model says:
 *
 * > "a lot of treatments don't have date, only created_at, some of them with string others with
 * > long..."
 *
 * So mixed types in that field are not hypothetical, they are the documented reason the field looks
 * the way it does. A strict parser would start throwing on records that parse fine today, and
 * because `NSClientV3Service.onDataCreateUpdate` is a socket.io listener with no try/catch, that
 * would escape onto the socket callback thread and drop the record.
 *
 * These tests record today's behaviour. After the converter switch they must still pass - if one
 * cannot, that is a deliberate decision to make, not something to discover in the field.
 */
class WireTypeCoercionTest {

    private fun parse(json: String): RemoteTreatment = nsSdkJson.decodeFromString(RemoteTreatment.serializer(), json)

    // ---------------------------------------------------------------- number into a String field

    /** The documented real-world case: `created_at` sent as an epoch number, not a string. */
    @Test
    fun `created_at as a number is coerced to a string`() {
        val treatment = parse("""{"eventType":"Correction Bolus","created_at":1785992179555,"insulin":0.25}""")

        assertThat(treatment.created_at).isEqualTo("1785992179555")
    }

    /**
     * An epoch written as a number now survives into the timestamp.
     *
     * This used to give `0L` - joda's ISO parser cannot read "1785992179555", and the catch turned
     * that into the epoch, so the treatment landed in 1970 with no error anywhere. It was pinned as
     * broken while the converter was being swapped, then fixed separately.
     *
     * Only reachable when `date`, `mills` and `timestamp` are all absent, which AAPS never produces
     * but older API v1 documents and other uploaders do.
     */
    @Test
    fun `a numeric created_at now survives into the timestamp`() {
        val treatment = parse("""{"eventType":"Correction Bolus","created_at":1785992179555}""")

        assertThat(treatment.timestamp()).isEqualTo(1785992179555L)
    }

    /** The other three fields still win, in order - the fallback must not change that. */
    @Test
    fun `date mills and timestamp still take precedence over created_at`() {
        val allFour = """{"eventType":"Correction Bolus","date":111,"mills":222,"timestamp":333,"created_at":444}"""
        assertThat(parse(allFour).timestamp()).isEqualTo(111L)

        val noDate = """{"eventType":"Correction Bolus","mills":222,"timestamp":333,"created_at":444}"""
        assertThat(parse(noDate).timestamp()).isEqualTo(222L)

        val onlyTimestamp = """{"eventType":"Correction Bolus","timestamp":333,"created_at":444}"""
        assertThat(parse(onlyTimestamp).timestamp()).isEqualTo(333L)
    }

    /** A quoted epoch is the same value as a bare one - the coercion happens before we see it. */
    @Test
    fun `a quoted epoch created_at also works`() {
        val treatment = parse("""{"eventType":"Correction Bolus","created_at":"1785992179555"}""")

        assertThat(treatment.timestamp()).isEqualTo(1785992179555L)
    }

    /**
     * Seconds are deliberately NOT converted. `1525383610` is a plausible epoch in seconds and also a
     * real millisecond epoch (17 January 1970), and the document does not say which. Guessing would
     * swap a visible 1970 date for an invisible wrong one.
     */
    @Test
    fun `an epoch in seconds is taken literally, not multiplied`() {
        val treatment = parse("""{"eventType":"Correction Bolus","created_at":1525383610}""")

        assertThat(treatment.timestamp()).isEqualTo(1525383610L)
    }

    /** The ISO string form, which does work. */
    @Test
    fun `an ISO created_at parses into the timestamp`() {
        val treatment = parse("""{"eventType":"Correction Bolus","created_at":"2026-08-06T04:56:19.555Z"}""")

        assertThat(treatment.timestamp()).isEqualTo(1785992179555L)
    }

    /** Unparseable text is swallowed rather than thrown - the record survives with timestamp 0. */
    @Test
    fun `an unparseable created_at gives timestamp zero, it does not throw`() {
        val treatment = parse("""{"eventType":"Correction Bolus","created_at":"whenever"}""")

        assertThat(treatment.timestamp()).isEqualTo(0L)
    }

    // ---------------------------------------------------------------- numeric string into a number field

    @Test
    fun `a numeric string is coerced into a Long field`() {
        val treatment = parse("""{"eventType":"Correction Bolus","date":"1785992179555"}""")

        assertThat(treatment.date).isEqualTo(1785992179555L)
    }

    @Test
    fun `a numeric string is coerced into a Double field`() {
        val treatment = parse("""{"eventType":"Correction Bolus","insulin":"0.25"}""")

        assertThat(treatment.insulin).isEqualTo(0.25)
    }

    @Test
    fun `a numeric string is coerced into an Int field`() {
        val treatment = parse("""{"eventType":"Profile Switch","percentage":"90"}""")

        assertThat(treatment.percentage).isEqualTo(90)
    }

    /** A whole-number JSON value landing in a Double field. */
    @Test
    fun `an integer is accepted by a Double field`() {
        val treatment = parse("""{"eventType":"Carb Correction","carbs":4}""")

        assertThat(treatment.carbs).isEqualTo(4.0)
    }

    // ---------------------------------------------------------------- booleans

    @Test
    fun `the string true is coerced into a Boolean field`() {
        val treatment = parse("""{"eventType":"Correction Bolus","isValid":"true"}""")

        assertThat(treatment.isValid).isTrue()
    }

    // ---------------------------------------------------------------- what still throws

    /** Text that is not a number at all still fails, in both libraries. */
    @Test
    fun `non numeric text in a number field throws`() {
        assertThrows<SerializationException> { parse("""{"eventType":"Correction Bolus","date":"not-a-number"}""") }
    }

    // ---------------------------------------------------------------- end to end through the mapper

    /**
     * The same coercions seen through the public entry point, so the pin covers what callers
     * actually use rather than only the wire model.
     */
    @Test
    fun `coerced values survive through toNSTreatment`() {
        val bolus = """{"eventType":"Correction Bolus","date":"1785992179555","insulin":"0.25","isValid":"true"}"""
            .toNSTreatment()
        assertThat(bolus).isInstanceOf(NSBolus::class.java)
        assertThat((bolus as NSBolus).insulin).isEqualTo(0.25)
        assertThat(bolus.date).isEqualTo(1785992179555L)

        val carbs = """{"eventType":"Carb Correction","date":1785987360000,"carbs":"4"}""".toNSTreatment()
        assertThat(carbs).isInstanceOf(NSCarbs::class.java)
        assertThat((carbs as NSCarbs).carbs).isEqualTo(4.0)
    }
}
