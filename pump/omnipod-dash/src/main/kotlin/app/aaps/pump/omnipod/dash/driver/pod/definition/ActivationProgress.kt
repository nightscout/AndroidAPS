package app.aaps.pump.omnipod.dash.driver.pod.definition

enum class ActivationProgress {
    NOT_STARTED,
    GOT_POD_VERSION,
    SET_UNIQUE_ID,
    PROGRAMMED_LOW_RESERVOIR_ALERTS,
    REPROGRAMMED_LUMP_OF_COAL_ALERT,
    PRIMING,
    PRIME_COMPLETED,
    PHASE_1_COMPLETED,
    PROGRAMMED_BASAL,
    UPDATED_EXPIRATION_ALERTS,
    INSERTING_CANNULA,
    CANNULA_INSERTED,
    COMPLETED;

    fun isBefore(other: ActivationProgress): Boolean = ordinal < other.ordinal

    fun isAtLeast(other: ActivationProgress): Boolean = ordinal >= other.ordinal
}
