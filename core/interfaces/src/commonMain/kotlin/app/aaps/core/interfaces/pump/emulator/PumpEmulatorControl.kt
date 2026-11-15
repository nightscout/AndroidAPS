package app.aaps.core.interfaces.pump.emulator

/**
 * What a test can do to an in-tree pump emulator: faults, events and speed, and a look at what the emulated pump did.
 *
 * Only tests use it. The driver never sees it: it talks to the emulator through its normal transport, so it cannot tell
 * a test from a real pump. Each hook copies something seen on a real pump.
 *
 * A hook the emulated pump cannot do returns false. A scenario then reports the case as not supported for that pump,
 * so a missing hook never looks like a test that passed.
 */
interface PumpEmulatorControl {

    /**
     * How long the pump takes for what it does: a bolus, priming, a motor move. Its clock is always the phone's clock,
     * or its history would have times no real pump gives. Things that wait for a time, as the end of a temp basal, are
     * not made shorter: a test raises them as events.
     */
    var speed: EmulatorSpeed

    /** The link breaks now, as when the pump goes out of range for a moment. The next connect works. */
    fun breakLink(): Boolean

    /** While true, the pump cannot be reached: a connect never comes up. */
    fun setOutOfRange(outOfRange: Boolean): Boolean

    /**
     * The next [command] never reaches the pump: the pump does nothing and sends nothing. Lost commands are used in the
     * order they were set: one waits until those set before it are used.
     */
    fun loseCommand(command: EmulatedCommand): Boolean

    /**
     * The pump runs the next [command], and its answer is lost. For the app this is not the same as [loseCommand].
     * Lost answers are used in the order they were set, so a test can name a sequence: "the cancel answer is lost, then
     * the answer to the status read after it".
     */
    fun loseAnswer(command: EmulatedCommand): Boolean

    /** The pump reports [alarm] now. */
    fun raise(alarm: EmulatedAlarm): Boolean

    /** The next bolus stops after [units], as at an occlusion. */
    fun stopNextBolusAt(units: Double): Boolean

    /**
     * The pump clock goes [offsetMs] further off the phone clock, as a clock that drifted or was not set. A pump that logs
     * its own time then logs it off by that much. False for an emulator that cannot.
     */
    fun shiftPumpClock(offsetMs: Long): Boolean = false

    /**
     * The user gives a bolus of [units] on the pump itself, without the app, and the pump logs it as it logs any bolus.
     * The driver then finds it in the pump history. False for a pump that cannot give a bolus without the app, as a patch
     * with no buttons.
     */
    fun bolusOnPump(units: Double): Boolean = false

    /**
     * The user stops ([suspended] true) or starts again the insulin on the pump itself, without the app, and the pump
     * logs it as it logs any stop. False for a pump that cannot be stopped without the app, as a patch with no buttons.
     */
    fun suspendOnPump(suspended: Boolean): Boolean = false

    /** What the emulated pump holds now. */
    fun state(): EmulatedPumpState

    /**
     * All the emulated pump holds, with the bolus it is giving, for a test that kills the app and starts it again in a
     * new process. Each emulator reads its own format back. Null when it cannot save.
     */
    fun saveState(): ByteArray?
}

enum class EmulatorSpeed {

    /** As a real pump. For the runs that look for timing faults. */
    REAL,

    /** As short as the driver allows, for the normal test runs. */
    FAST
}

/** Commands by what they do, so a test can name one for any pump. */
enum class EmulatedCommand {

    BOLUS_START,
    BOLUS_STOP,
    TEMP_BASAL_START,
    TEMP_BASAL_STOP,
    EXTENDED_BOLUS_START,
    EXTENDED_BOLUS_STOP,
    BASAL_PROFILE,

    /** A read of what the pump runs now: its status, or its temp basal. Not a read of its history. */
    STATUS
}

/** Alarms by what they mean. Each emulator sends them with its own codes. */
enum class EmulatedAlarm {

    LOW_RESERVOIR,
    EMPTY_RESERVOIR,
    OCCLUSION,
    LOW_BATTERY,
    STOPPED_TOO_LONG
}

/**
 * @param boluses every bolus the pump gave, oldest first, each with the amount it really gave
 * @param bolusRunning a bolus is being given now
 * @param tempBasal the temp basal that runs now, or null
 * @param suspended the pump gives no insulin
 * @param reservoir insulin left in units, or null when the pump does not say
 * @param linkUp a phone is connected now
 * @param commands how many commands the pump has received since it started. A step that sends the same command again
 * and again shows here.
 * @param extendedBolus the extended bolus that runs now, or null. An emulator of a pump that can give one must fill it,
 * or the scenario finds an extended bolus in AAPS that the pump seems not to run.
 * @param battery battery left in percent, or null when the pump does not say
 * @param basal the basal the pump gives in each hour of the day, 24 rates in U/h from midnight, or null when the
 * emulator cannot say. A scenario compares it with the profile AAPS runs.
 */
data class EmulatedPumpState(
    val boluses: List<Double>,
    val bolusRunning: Boolean,
    val tempBasal: EmulatedTempBasal?,
    val suspended: Boolean,
    val reservoir: Double?,
    val linkUp: Boolean,
    val commands: Int,
    val extendedBolus: EmulatedExtendedBolus? = null,
    val battery: Int? = null,
    val basal: List<Double>? = null
)

/** A pump sets a temp basal either as a rate or as a percent of the basal, so one of the two is set. */
data class EmulatedTempBasal(val absolute: Double?, val percent: Int?)

/** An extended bolus: [units] over [minutes]. */
data class EmulatedExtendedBolus(val units: Double, val minutes: Int)
