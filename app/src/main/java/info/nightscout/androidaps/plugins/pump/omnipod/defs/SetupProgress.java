package info.nightscout.androidaps.plugins.pump.omnipod.defs;

public enum SetupProgress {
    ADDRESS_ASSIGNED,
    POD_CONFIGURED,
    STARTING_PRIME,
    PRIMING,
    PRIMING_FINISHED,
    INITIAL_BASAL_SCHEDULE_SET,
    STARTING_INSERT_CANNULA,
    CANNULA_INSERTING,
    COMPLETED;

    public boolean isBefore(SetupProgress other) {
        return this.ordinal() < other.ordinal();
    }

    public boolean isAfter(SetupProgress other) {
        return this.ordinal() > other.ordinal();
    }
}