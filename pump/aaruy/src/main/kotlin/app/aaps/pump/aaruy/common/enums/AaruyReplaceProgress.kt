package app.aaps.pump.aaruy.common.enums

/**
 * How far a change of reservoir came, read from the pump log. The log has no entry for the fill of the cannula, so it
 * cannot say whether that was done.
 */
enum class AaruyReplaceProgress {
    NONE,
    REWOUND,
    PRIMED
}
