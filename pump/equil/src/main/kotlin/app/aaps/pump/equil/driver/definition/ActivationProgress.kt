package app.aaps.pump.equil.driver.definition

enum class ActivationProgress {
    NONE,
    PRIMING,
    CANNULA_CHANGE,
    CANNULA_INSERTED,
    COMPLETED;

    open fun isBefore(other: ActivationProgress): Boolean {
        return ordinal < other.ordinal
    }

    open fun isAtLeast(other: ActivationProgress): Boolean {
        return ordinal >= other.ordinal
    }

    open fun isAfter(other: ActivationProgress): Boolean {
        return ordinal > other.ordinal
    }
}