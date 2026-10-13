package app.aaps.pump.omnipod.eros.driver.definition;

public enum ActivationProgress {
    NONE,
    PAIRING_COMPLETED,
    TAB_5_SUB_16_AND_17_DISABLED,
    SETUP_REMINDERS_SET,
    PRIMING,
    PRIMING_COMPLETED,
    BASAL_INITIALIZED,
    EXPIRATION_REMINDERS_SET,
    INSERTING_CANNULA,
    COMPLETED;

    public boolean needsPairing() {
        return this == NONE;
    }

    public boolean needsDisableTab5Sub16And17() {
        return this == PAIRING_COMPLETED;
    }

    public boolean needsSetupReminders() {
        return this == TAB_5_SUB_16_AND_17_DISABLED;
    }

    public boolean needsPriming() {
        return this == SETUP_REMINDERS_SET;
    }

    public boolean needsPrimingVerification() {
        return this == PRIMING;
    }

    public boolean needsBasalSchedule() {
        return this == PRIMING_COMPLETED;
    }

    public boolean needsExpirationReminders() {
        return this == BASAL_INITIALIZED;
    }

    public boolean needsCannulaInsertion() {
        return this == EXPIRATION_REMINDERS_SET;
    }

    public boolean needsCannulaInsertionVerification() {
        return this == INSERTING_CANNULA;
    }

    public boolean isCompleted() {
        return this == COMPLETED;
    }

    public boolean isBefore(ActivationProgress other) {
        return ordinal() < other.ordinal();
    }

    public boolean isAtLeast(ActivationProgress other) {
        return ordinal() >= other.ordinal();
    }

    public boolean isAfter(ActivationProgress other) {
        return ordinal() > other.ordinal();
    }
}