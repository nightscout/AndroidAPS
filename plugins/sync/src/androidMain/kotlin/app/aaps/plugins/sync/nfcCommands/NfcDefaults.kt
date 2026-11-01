package app.aaps.plugins.sync.nfcCommands

/**
 * Every default value the NFC plugin uses, in one place.
 *
 * These belong to the plugin and not to therapy, so they are here rather than in the shared
 * `Constants`: nothing outside the plugin reads them, and putting them there would make them look
 * app-wide.
 *
 * They used to be written as literals wherever they were needed, and the same value was written
 * differently in different files. The duration a command fell back to was 0, 30 or 60 depending which
 * action you read, and the build screen filled a missing carb value with 20 g while the executor used
 * 0 g. One name each means the screen and the action cannot disagree any more, and a value can be
 * changed in one edit.
 *
 * A value here is what a **new** command starts with, handed out by the action's `getDefaultParams()`.
 * It is not a stand-in for a value the user never set: a stored command missing one of the arguments it
 * declares is refused rather than filled in, see
 * [app.aaps.plugins.sync.nfcCommands.actions.NfcAction.executeIfComplete].
 */
object NfcDefaults {

    // What a new command starts with.
    const val BOLUS_INSULIN: Double = 0.0
    const val CARBS_GRAMS: Int = 0
    const val EXTENDED_BOLUS_INSULIN: Double = 0.0
    const val EXTENDED_BOLUS_DURATION_MINUTES: Int = 30
    const val LOOP_SUSPEND_DURATION_MINUTES: Int = 60
    const val PUMP_DISCONNECT_DURATION_MINUTES: Int = 30
    const val PROFILE_SWITCH_PERCENT: Int = 100
    const val TEMP_BASAL_RATE: Double = 0.0
    const val TEMP_BASAL_PERCENT: Int = 100
    const val TEMP_TARGET_DURATION_MINUTES: Int = 60

    /** The bolus wizard percentage, and what a reading too old to use resets it to. */
    const val WIZARD_PERCENTAGE: Int = 100

    /** Used when the profile has no low target to read. In mg/dl, like the profile itself. */
    const val TEMP_TARGET_MGDL_WITHOUT_PROFILE: Double = 100.0

    /** The pump's temporary basal duration step, in minutes, when the pump does not say. */
    const val PUMP_BASAL_DURATION_STEP_MINUTES: Int = 60

    // A value is held inside its range before it is used.
    val PROFILE_SWITCH_PERCENT_RANGE: IntRange = 10..500
    val PUMP_DISCONNECT_DURATION_RANGE: IntRange = 1..180

    /**
     * Shown for a glucose target with no value.
     *
     * Only reachable on a command the tag list has already marked incomplete, because such a command is
     * never run. There is no better number to use: the real starting value comes from the profile.
     */
    const val GLUCOSE_TARGET_UNSET: Double = 0.0
}
