package app.aaps.plugins.libre3.session

/**
 * What the driver is allowed to do when it wants to open a session.
 *
 * This is the single place where the most dangerous choice of the whole driver is made: whether to
 * send the first pairing command or the short reconnect command.
 */
enum class Libre3SessionStart {

    /**
     * Full first pairing. Only for a sensor that this phone has never paired.
     *
     * It starts with the command `0x01`. A sensor that is already running refuses that command
     * late in the handshake, so sending it to such a sensor wastes a whole connection and hides
     * the real problem behind a wrong error.
     */
    FIRST_PAIR,

    /**
     * Short reconnect, command `0x11` only.
     *
     * Used whenever a pairing key is stored. If it fails, the answer is never to try the first
     * pairing: the user has to hold the phone on the sensor again so a new NFC step can run.
     */
    CACHED_RECONNECT,

    /** Nothing may be sent. The reason is in [Libre3StartRefusal]. */
    BLOCKED,
}

/** Why a session may not start. */
enum class Libre3StartRefusal {

    /** No sensor has been scanned with NFC on this phone yet. */
    NO_SENSOR_STORED,

    /**
     * The sensor was scanned but its PIN is not on disk yet.
     *
     * Starting Bluetooth now could take the sensor over with a PIN that is then lost, and the
     * sensor would be unusable without another NFC scan.
     */
    PIN_NOT_WRITTEN_YET,

    /** The first pairing scheme is not available in this build, so a new sensor cannot be paired. */
    FIRST_PAIR_NOT_AVAILABLE,
}

/** The decision, and the reason when nothing may be sent. */
data class Libre3StartDecision(val start: Libre3SessionStart, val refusal: Libre3StartRefusal? = null)

/**
 * Decides how a session may start. Pure, so it is unit tested without any radio.
 *
 * Rules, in order:
 * 1. Nothing at all without a stored sensor.
 * 2. Nothing at all until the PIN write has finished. This is the rule that protects a sensor
 *    from being taken over and then stranded.
 * 3. A stored pairing key means the short reconnect, and only the short reconnect.
 * 4. No pairing key means a first pairing, if this build can do one.
 */
object Libre3SessionStartPolicy {

    fun decide(
        hasStoredSensor: Boolean,
        pinWriteFinished: Boolean,
        hasPairingKey: Boolean,
        firstPairAvailable: Boolean,
    ): Libre3StartDecision {
        if (!hasStoredSensor) {
            return Libre3StartDecision(Libre3SessionStart.BLOCKED, Libre3StartRefusal.NO_SENSOR_STORED)
        }
        if (!pinWriteFinished) {
            return Libre3StartDecision(Libre3SessionStart.BLOCKED, Libre3StartRefusal.PIN_NOT_WRITTEN_YET)
        }
        if (hasPairingKey) {
            return Libre3StartDecision(Libre3SessionStart.CACHED_RECONNECT)
        }
        if (!firstPairAvailable) {
            return Libre3StartDecision(Libre3SessionStart.BLOCKED, Libre3StartRefusal.FIRST_PAIR_NOT_AVAILABLE)
        }
        return Libre3StartDecision(Libre3SessionStart.FIRST_PAIR)
    }
}
