package app.aaps.pump.aaruy.services

enum class AaruyBolusOutcome {

    /** The start never went to the pump. */
    NOT_STARTED,

    /** The pump finished the bolus, or confirmed a stop the user asked for. */
    FINISHED,

    /** The pump ended the bolus by itself before all was given, as with a blocked motor. A failed bolus with the real amount. */
    CUT_SHORT,

    /** The link was lost after the start was sent. The pump may deliver all of it, so this is not a failed bolus. */
    UNKNOWN
}

/** What came of sending the start. The pump answers it only with a status. */
enum class AaruyBolusStart {

    /** The pump reported the bolus. */
    STARTED,

    /** The start was not sent and never will be. */
    NOT_SENT,

    /** The start went to the radio, and no status showed the bolus. The pump may run it. */
    NO_ANSWER
}
