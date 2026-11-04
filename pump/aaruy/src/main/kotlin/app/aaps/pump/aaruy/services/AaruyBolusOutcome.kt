package app.aaps.pump.aaruy.services

enum class AaruyBolusOutcome {

    /** The pump did not report that it started. */
    NOT_STARTED,

    /** The pump finished the bolus, or confirmed a stop. */
    FINISHED,

    /** The link was lost after the pump started. It may still deliver the rest, so this is not a failed bolus. */
    UNKNOWN
}
