package app.aaps.plugins.libre3.session

import app.aaps.plugins.libre3.Libre3Log
import app.aaps.plugins.libre3.Libre3LogMarkers
import app.aaps.plugins.libre3.libre3HexLower
import kotlin.time.Clock

/**
 * Step by step trace of one pairing attempt.
 *
 * It exists for one question the ordinary log cannot answer: when a pairing fails, was our answer
 * wrong, or were we simply too slow? The driver logs one line when the pairing starts and one when
 * it fails, and between the two there is nothing, so a sensor that hangs up because it waited too
 * long looks exactly like a sensor that refused a bad key.
 *
 * So every step gets a line with two numbers: how long that step took, and how long the whole
 * attempt has been running. A step that takes seconds is then plain to see.
 *
 * The bytes matter as much as the clock. A pairing that fails at the last step fails because one
 * of the values below is not what the sensor expected, and the only way to find out which is to
 * compare them with a trace from a reference implementation. That is why the trace can write the
 * values down, but only when [TRACE_SECRETS] is on. Otherwise every value is written as its length
 * only.
 *
 * ⚠️ [TRACE_SECRETS] writes key material, the sensor certificate and the whole pairing exchange to
 * `AndroidAPS.log`, and users send that log to other people when they ask for help. It must stay
 * false in every build that is given to users. Turn it on only in a local build while a pairing
 * is being debugged, and never share a log taken with it on outside the people debugging this
 * sensor.
 *
 * @param write where a trace line goes. Tests replace it to read the lines back.
 */
class Libre3PairingTrace(
    private val nowMs: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val write: (String) -> Unit = { Libre3Log.i(it) },
) {

    private val startedAt = nowMs()
    private var lastStepAt = startedAt

    /**
     * Writes one step down.
     *
     * @param step what just finished, in a few words.
     */
    fun mark(step: String) {
        val now = nowMs()
        val sinceLast = now - lastStepAt
        lastStepAt = now
        write("${Libre3LogMarkers.TRACE}: $step +${sinceLast}ms (t=${now - startedAt}ms)")
    }

    /**
     * Writes down a value that travels on the radio.
     *
     * Such a value is not a key, but it is tied to this user's sensor (the certificate, the
     * challenge, the encrypted answers), so only the length is logged unless [TRACE_SECRETS] is on.
     */
    fun bytes(label: String, value: ByteArray) = writeValue(label, value)

    /**
     * Writes down a value that never leaves the phone.
     *
     * Only the length is logged unless [TRACE_SECRETS] is on.
     */
    fun secret(label: String, value: ByteArray) = writeValue(label, value)

    private fun writeValue(label: String, value: ByteArray) {
        if (TRACE_SECRETS) {
            write("${Libre3LogMarkers.TRACE}: $label ${value.size} bytes ${hex(value)}")
        } else {
            write("${Libre3LogMarkers.TRACE}: $label ${value.size} bytes, not shown")
        }
    }

    companion object {

        /**
         * Whether raw pairing bytes, key material included, are written to the log.
         *
         * Must stay false in every build given to users. See the warning on this class.
         */
        const val TRACE_SECRETS = false

        fun hex(value: ByteArray): String = value.libre3HexLower()
    }
}
