package app.aaps.core.nssdk.remotemodel

import app.aaps.core.nssdk.localmodel.entry.NsUnits
import app.aaps.core.nssdk.localmodel.treatment.EventType
import app.aaps.core.nssdk.localmodel.treatment.NSOfflineEvent
import app.aaps.core.nssdk.localmodel.treatment.NSTemporaryTarget
import app.aaps.core.nssdk.localmodel.treatment.NSTherapyEvent
import app.aaps.core.nssdk.localmodel.treatment.NSTreatment
import app.aaps.core.nssdk.mapper.toNSTreatment
import app.aaps.core.nssdk.mapper.toRemoteTreatment
import app.aaps.core.nssdk.nsSdkJson
import app.aaps.core.nssdk.throughJavaScript
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal

/**
 * "No end" ([Long.MAX_VALUE]) through Nightscout, which runs on JavaScript. [throughJavaScript] does to a
 * document what Nightscout does to it (checked against Node in `JavaScriptJsonTest`).
 *
 * Every entry AAPS keeps without an end: the temporary target and the therapy event of a scene without a
 * duration, a running mode forced by a constraint. Before, Nightscout gave their duration back as
 * 9223372036854776000, which failed the whole page of treatments (#5626).
 */
class WireDurationTest {

    private val endlessTarget = NSTemporaryTarget(
        date = 1_000L, identifier = "tt", units = NsUnits.MG_DL, utcOffset = 0, eventType = EventType.TEMPORARY_TARGET,
        pumpId = null, endId = null, pumpType = null, pumpSerial = null,
        duration = Long.MAX_VALUE, targetBottom = 80.0, targetTop = 80.0, reason = NSTemporaryTarget.Reason.ACTIVITY
    )

    private val endlessEvent = NSTherapyEvent(
        date = 1_000L, identifier = "te", units = null, utcOffset = 0, isValid = true, eventType = EventType.EXERCISE,
        notes = "scene", pumpId = null, endId = null, pumpType = null, pumpSerial = null, duration = Long.MAX_VALUE
    )

    /** A pump suspended without an end: the running mode that keeps [Long.MAX_VALUE] in both durations. */
    private val endlessSuspend = NSOfflineEvent(
        date = 1_000L, identifier = "rm", utcOffset = 0, isValid = true, eventType = EventType.APS_OFFLINE,
        pumpId = null, endId = null, pumpType = null, pumpSerial = null,
        duration = Long.MAX_VALUE, originalDuration = Long.MAX_VALUE, reason = NSOfflineEvent.Reason.OTHER,
        mode = NSOfflineEvent.Mode.SUSPENDED_BY_PUMP, autoForced = true, reasons = "pump"
    )

    private val endless = listOf(endlessTarget, endlessEvent, endlessSuspend)

    private fun upload(treatment: NSTreatment): String =
        nsSdkJson.encodeToString(RemoteTreatment.serializer(), requireNotNull(treatment.toRemoteTreatment()))

    private fun download(json: String): NSTreatment = requireNotNull(json.toNSTreatment())

    private fun durationOf(treatment: NSTreatment): Long = when (treatment) {
        is NSTemporaryTarget -> treatment.duration
        is NSTherapyEvent    -> treatment.duration
        is NSOfflineEvent    -> treatment.duration
        else                 -> error("not an entry with a duration: $treatment")
    }

    @Test
    fun `an entry without an end keeps it through Nightscout`() {
        for (treatment in endless) {
            val back = download(throughJavaScript(upload(treatment)))

            assertThat(durationOf(back)).isEqualTo(Long.MAX_VALUE)
        }
        val suspend = download(throughJavaScript(upload(endlessSuspend))) as NSOfflineEvent
        assertThat(suspend.originalDuration).isEqualTo(Long.MAX_VALUE)
    }

    /**
     * No number AAPS sends may change its value in JavaScript, or Nightscout keeps another value than AAPS
     * sent. Compared as values: JavaScript writes 80.0 as 80, which is the same number.
     */
    @Test
    fun `JavaScript does not change what AAPS sends`() {
        for (treatment in endless) {
            val sent = upload(treatment)

            val numbers = numbersIn(Json.parseToJsonElement(sent))
            val inNightscout = numbersIn(Json.parseToJsonElement(throughJavaScript(sent)))
            assertThat(numbers.zip(inNightscout).filter { (a, b) -> a.compareTo(b) != 0 }).isEmpty()
            assertThat(sent).doesNotContain(Long.MAX_VALUE.toString())
        }
    }

    private fun numbersIn(element: JsonElement): List<BigDecimal> = when (element) {
        is JsonObject    -> element.values.flatMap(::numbersIn)
        is JsonArray     -> element.flatMap(::numbersIn)
        is JsonPrimitive -> if (element.isString || element is JsonNull || element.booleanOrNull != null) emptyList() else listOf(BigDecimal(element.content))
    }

    /**
     * The records an older AAPS already left in Nightscout: it sent [Long.MAX_VALUE], Nightscout keeps the
     * rounded 9223372036854776000. They must read as "no end", whatever the event type and the field.
     */
    @Test
    fun `an entry without an end that an older AAPS left in Nightscout reads again`() {
        for (treatment in endless) {
            val olderUpload = upload(treatment).replace(JS_MAX_SAFE_INTEGER.toString(), Long.MAX_VALUE.toString())
            val inNightscout = throughJavaScript(olderUpload)
            assertThat(inNightscout).contains("9223372036854776000")

            assertThat(durationOf(download(inNightscout))).isEqualTo(Long.MAX_VALUE)
        }
    }

    /** The running modes the PR #5643 looked at, and the one it missed: OPEN_LOOP is forced the same way. */
    @Test
    fun `a forced running mode an older AAPS left in Nightscout reads again in any mode`() {
        for (mode in listOf("CLOSED_LOOP_LGS", "OPEN_LOOP", "DISABLED_LOOP")) {
            val inNightscout = """{"identifier":"rm","date":1000,"eventType":"OpenAPS Offline","mode":"$mode","duration":0,"autoForced":true,"originalDuration":9223372036854776000}"""

            val back = download(inNightscout) as NSOfflineEvent

            assertThat(back.originalDuration).isEqualTo(Long.MAX_VALUE)
            assertThat(back.mode.name).isEqualTo(mode)
        }
    }

    @Test
    fun `an ordinary duration is not changed`() {
        val halfAnHour = endlessTarget.copy(duration = 1_800_000L)

        val sent = upload(halfAnHour)

        assertThat(sent).contains("\"durationInMilliseconds\":1800000")
        assertThat((download(throughJavaScript(sent)) as NSTemporaryTarget).duration).isEqualTo(1_800_000L)
    }

    /** The lenient parser read a number sent as a string before; the duration fields still do. */
    @Test
    fun `a duration sent as a string is still read`() {
        val json = """{"identifier":"tt","date":1000,"eventType":"Temporary Target","units":"mg/dl","targetBottom":80,"targetTop":80,"durationInMilliseconds":"1800000"}"""

        assertThat((download(json) as NSTemporaryTarget).duration).isEqualTo(1_800_000L)
    }

    @Test
    fun `a duration that is no number is still an error`() {
        val json = """{"identifier":"tt","date":1000,"eventType":"Temporary Target","units":"mg/dl","targetBottom":80,"targetTop":80,"durationInMilliseconds":"soon"}"""

        assertThrows<SerializationException> { download(json) }
    }
}
