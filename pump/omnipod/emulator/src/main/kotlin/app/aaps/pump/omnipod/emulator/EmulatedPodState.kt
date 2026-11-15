package app.aaps.pump.omnipod.emulator

import java.io.Serializable

/**
 * All an emulated Omnipod Dash pod holds. Serializable, so a test that kills the app can keep the pod as it was.
 *
 * Insulin is counted in pulses of 0.05 U, as the pod does.
 */
class EmulatedPodState : Serializable {

    /** The Bluetooth address the scan finds. A new pod has a new one. */
    var address: String = "00:00:00:00:00:00"

    /** Lot and sequence number, the serial of a pod. Never the numbers of a real pod: they are random. */
    var lotNumber: Int = 0
    var podSequenceNumber: Int = 0

    /** The long term key of the pairing, or null while the pod is not paired. */
    var ltk: ByteArray? = null

    /** The id the controller gave this pod with the "set unique id" command, or null before it. */
    var uniqueId: Int? = null

    /** The pod status byte, see `PodStatus` in the driver. A new pod is FILLED. */
    var podStatus: Int = STATUS_FILLED

    /** The phone time of the "set unique id" command. The pod counts its minutes from it. */
    var activatedAt: Long = 0

    /** Pulses left in the reservoir. */
    var reservoirPulses: Int = NEW_POD_PULSES

    /** Every pulse the pod gave: priming, basal, temp basal and bolus. */
    var totalPulses: Int = 0

    /** The sequence number of the last command that changed what the pod does. The driver confirms a command with it. */
    var lastProgrammingSequence: Int = 0

    /** The alert bits that are active now, see `AlertType` in the driver. */
    var activeAlerts: Int = 0

    /** For each of the 8 alert slots, the pod minute at which it fired, or 0. */
    val alertMinutes: IntArray = IntArray(8)

    /** The bolus the pod gives now, or null. */
    var bolus: RunningPodBolus? = null

    /**
     * Pulses of the last bolus that were not given, after a stop. It stays until the next bolus starts.
     * UNVERIFIED: that a real pod keeps reporting it in later status answers. The driver counts on it: it takes the
     * amount from every status answer.
     */
    var bolusPulsesNotDelivered: Int = 0

    /** The temp basal that runs now, or null. */
    var tempBasal: RunningPodTempBasal? = null

    /** Pulses of each half hour of the day, 48 values from midnight, or null before the basal program is set. */
    var basalPulsesPerSlot: IntArray? = null

    /** The phone time at which the pod's day starts, as the basal program told it. */
    var basalDayStart: Long = 0

    /** The pod gives no basal: stopped by the controller. */
    var suspended: Boolean = false

    /** Basal and temp basal insulin not yet given as a whole pulse, in pulses. */
    var basalFraction: Double = 0.0

    /** Until when the basal is counted. */
    var settledAt: Long = 0

    /** The fault of the pod, an `AlarmType` value, or 0. A pod with a fault gives no insulin and cannot be used again. */
    var alarmType: Int = 0
    var alarmMinutes: Int = 0
    var podStatusAtAlarm: Int = 0

    /** Every bolus the pod gave for the user, oldest first, in units. Not the priming. */
    val deliveredBoluses: MutableList<Double> = mutableListOf()

    /** How many commands reached the pod. */
    var commands: Int = 0

    val paired: Boolean get() = ltk != null
    val faulted: Boolean get() = alarmType != 0
    val running: Boolean get() = podStatus == STATUS_RUNNING_ABOVE_MIN_VOLUME || podStatus == STATUS_RUNNING_BELOW_MIN_VOLUME

    companion object {

        private const val serialVersionUID = 1L

        const val PULSE_UNITS = 0.05

        const val STATUS_FILLED = 0x02
        const val STATUS_UID_SET = 0x03
        const val STATUS_ENGAGING_CLUTCH_DRIVE = 0x04
        const val STATUS_CLUTCH_DRIVE_ENGAGED = 0x05
        const val STATUS_BASAL_PROGRAM_SET = 0x06
        const val STATUS_PRIMING = 0x07
        const val STATUS_RUNNING_ABOVE_MIN_VOLUME = 0x08
        const val STATUS_RUNNING_BELOW_MIN_VOLUME = 0x09
        const val STATUS_ALARM = 0x0d
        const val STATUS_DEACTIVATED = 0x0f

        /** The pod says how much is left only from 50 U down. Above it says "more than 50 U". */
        const val REPORTED_RESERVOIR_PULSES = 1000

        /** Pulses of the two priming steps, as a real pod reports them (see `SetUniqueIdResponseTest`). */
        const val PRIME_PULSES = 52
        const val CANNULA_PULSES = 10

        /**
         * NOT AS A REAL POD. A real pod is filled with 85 U or more, and says only "more than 50 U" until 50 U are left:
         * then AAPS shows a fixed 75 U and a test cannot follow a bolus by the reservoir. The emulated pod is filled so
         * that exactly 50 U are left after the priming. It then reports its reservoir from the first bolus on, as a real
         * pod does after some days.
         */
        const val NEW_POD_PULSES = REPORTED_RESERVOIR_PULSES + PRIME_PULSES + CANNULA_PULSES
    }
}

/**
 * A bolus as the pod gives it: [pulses] pulses, one each [pulseMs]. [priming] is true for the two boluses of the
 * activation, which fill the cannula and are no insulin for the user.
 */
class RunningPodBolus(val startedAt: Long, val pulses: Int, val pulseMs: Long, val priming: Boolean) : Serializable {

    /** The pod stops with a fault once it gave this many pulses, or null. */
    var faultAtPulses: Int? = null

    /** Pulses given so far, already taken from the reservoir. */
    var givenPulses: Int = 0

    private companion object {

        private const val serialVersionUID = 1L
    }
}

/** A temp basal: [pulsesPerHour] until [endsAt]. */
class RunningPodTempBasal(val pulsesPerHour: Double, val endsAt: Long) : Serializable {

    private companion object {

        private const val serialVersionUID = 1L
    }
}
