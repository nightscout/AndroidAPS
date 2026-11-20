package app.aaps.plugins.libre3.nfc

import app.aaps.plugins.libre3.Libre3Log
import app.aaps.plugins.libre3.Libre3LogMarkers
import app.aaps.plugins.libre3.Libre3LogMask
import app.aaps.plugins.libre3.identity.Libre3IdentityStore
import app.aaps.plugins.libre3.identity.Libre3SensorIdentity
import app.aaps.plugins.libre3.identity.Libre3SensorStore
import app.aaps.plugins.libre3.libre3HexUpper
import kotlin.time.Clock

/**
 * Sends one command to the tag and returns the raw answer.
 *
 * On a phone this is `NfcV.transceive`. In unit tests it is a small fake, so the whole NFC flow can
 * be checked without any Android radio.
 */
fun interface Libre3NfcTransceiver {

    /** @throws Libre3NfcException when the tag does not answer or answers with an error. */
    fun transceive(frame: ByteArray): ByteArray
}

/** What one NFC scan produced. */
data class Libre3NfcScanResult(
    val patchInfo: Libre3PatchInfo,
    val commandUsed: Byte,
    /** The full answer of the sensor, kept so the activation time is not lost. */
    val activation: Libre3ActivationResponse,
    val identity: Libre3SensorIdentity,
    /** True only when the identity really reached the disk. Bluetooth may start only then. */
    val readyForBle: Boolean,
)

/**
 * Runs the whole NFC step: read the sensor, activate it or take it over, then store what came back.
 *
 * The order matters and is a safety rule. The PIN is written to disk, and the write is awaited,
 * before the result says [Libre3NfcScanResult.readyForBle]. A sensor that was taken over but whose
 * PIN was lost cannot be used again without a second scan.
 *
 * Ported from LibreCRKit `CoreNFCActivationReader.swift` and `NFCActivationCommand.swift` at pin
 * `a86b92f`. On Android the whole frame is built by hand, because `NfcV.transceive` does not add
 * the flag, the command code and the manufacturer code for us.
 *
 * ⚠️ ASYNC IMPACT: [scan] blocks while the tag answers. Call it from an input output thread, never
 * from the main thread and never from the Bluetooth executor.
 */
class Libre3NfcSession(
    private val store: () -> Libre3IdentityStore,
    private val nowMs: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    /**
     * Asked before the activation command is sent, with the serial just read from the patch info.
     * Returning true stops the scan there — the sensor is untouched and no PIN is written.
     */
    private val veto: (serialNumber: String) -> Boolean = { false },
) {

    /** The old shape, for every caller that has one fixed store and nothing to veto. */
    constructor(store: Libre3IdentityStore, nowMs: () -> Long = { Clock.System.now().toEpochMilliseconds() }) :
        this({ store }, nowMs)

    /**
     * @param transceiver link to the tag that is being held against the phone.
     * @return what was read and stored.
     * @throws Libre3NfcException when the tag is not a Libre 3, or an answer cannot be read.
     */
    fun scan(transceiver: Libre3NfcTransceiver): Libre3NfcScanResult {
        // The slot is read once, so a screen where the user changes the slot in the middle of a
        // scan can never read one file and then write the other.
        val identityStore = store()
        // The sensor binds itself to the receiver id it is given, so an id that could not be stored
        // must never be sent. The next scan would build a different one and the sensor would then
        // no longer answer this phone. The id is loaded before the tag is touched, so the gap
        // between A1 and A8 stays as short as the radio itself.
        val receiverId = try {
            identityStore.receiverId()
        } catch (e: IllegalStateException) {
            throw Libre3NfcException(
                "this phone could not store its receiver id, ${e.message}",
                Libre3NfcFailure.PHONE_STORAGE_FAILED,
            )
        }

        // A tag that is not a Libre 3 either refuses this command or answers something that is not
        // a patch info frame. Both end here, before any activation command can be sent.
        val patchInfo = Libre3NfcCommands.parsePatchInfo(transceiver.transceive(Libre3NfcCommands.patchInfoFrame()))
        Libre3Log.i(
            "${Libre3LogMarkers.NFC}: sensor read serial=${Libre3LogMask.serial(patchInfo.serialNumber)} " +
                "generation=${patchInfo.generation} state=0x${patchInfo.state.libre3HexUpper()} " +
                "warmupMinutes=${patchInfo.warmupMinutes} wearMinutes=${patchInfo.wearDurationMinutes}",
        )

        // The last moment at which this sensor is still untouched. One step further the activation
        // command replaces the PIN and the store drops the old sensor's keys, so a sensor that the
        // other slot already holds has to be refused here and nowhere later.
        if (veto(patchInfo.serialNumber)) {
            Libre3Log.w(
                "${Libre3LogMarkers.NFC}: scan stopped, serial=${Libre3LogMask.serial(patchInfo.serialNumber)} " +
                    "is held by the other slot",
            )
            throw Libre3NfcException(
                "this sensor is already held by the other slot",
                Libre3NfcFailure.SAME_SENSOR_OTHER_SLOT,
            )
        }

        val command = patchInfo.recommendedCommand
        val startedAtMs = nowMs()
        // The sensor keeps the time it is given, so it must be phone time in whole seconds. One
        // second is taken off, like the upstream code, so the sensor never gets a time that is at
        // or ahead of its own clock.
        val timeSeconds = maxOf(0L, startedAtMs / 1000L - 1L)
        val frame = Libre3NfcCommands.activationFrame(command, timeSeconds, receiverId)
        val activation = Libre3NfcCommands.parseActivationResponse(transceiver.transceive(frame))
        Libre3Log.i(
            "${Libre3LogMarkers.NFC}: command=0x${command.libre3HexUpper()} answered, " +
                "address=${Libre3LogMask.mac(activation.bleAddressDisplay)}",
        )

        // Sample times are counted from the activation moment, so it must be the sensor's own
        // moment, not the moment of this scan. A sensor that is taken over was activated hours or
        // days ago and reports that time itself. Only a sensor that this phone activates right now
        // may use the phone clock. When neither is known the value stays 0 and the ingest work
        // derives it from the first life counter.
        val sensorEpochSeconds = activation.activationEpochSeconds
        val activatedAtMs = when {
            sensorEpochSeconds > 0L                      -> sensorEpochSeconds * 1000L
            command == Libre3NfcUids.CMD_ACTIVATE        -> startedAtMs
            else                                         -> UNKNOWN_ACTIVATION_TIME
        }
        val identity = Libre3SensorIdentity(
            serialNumber = patchInfo.serialNumber,
            bleAddress = activation.bleAddressDisplay,
            blePin = activation.blePin,
            receiverId = receiverId,
            generation = patchInfo.generation,
            warmupMinutes = patchInfo.warmupMinutes.takeIf { it > 0 } ?: Libre3SensorStore.DEFAULT_WARMUP_MINUTES,
            wearDurationMinutes = patchInfo.wearDurationMinutes,
            activatedAtMs = activatedAtMs,
        )
        // Write first, then say that Bluetooth may start. Never the other way round.
        val stored = identityStore.saveIdentityAndWait(identity)
        if (!stored) {
            Libre3Log.e("${Libre3LogMarkers.ERROR}: sensor could not be stored, Bluetooth stays blocked")
        }
        return Libre3NfcScanResult(
            patchInfo = patchInfo,
            commandUsed = command,
            activation = activation,
            identity = identity,
            readyForBle = stored,
        )
    }

    companion object {

        /** Stored when neither the sensor nor this phone knows when the sensor was activated. */
        const val UNKNOWN_ACTIVATION_TIME = 0L
    }
}
