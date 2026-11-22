package app.aaps.pump.omnipod.common.bledriver.pod.definition

enum class ActivationProgress {
    NOT_STARTED,
    GOT_POD_VERSION,

    /** O5-only: the AID setup command batch (see [app.aaps.pump.omnipod.common.bledriver
     *  .pod.command.aid.O5AidSetupCommands]) has been sent. Confirmed directly against
     *  OmnipodKit's BlePodComms.swift: pairPod() sends AssignAddressCommand (opcode 0x07 -
     *  the exact same wire command as [app.aaps.pump.omnipod.common.bledriver.pod.command
     *  .GetVersionCommand] here, just kept under its historical Eros-era name upstream)
     *  and only *after* that completes does handleO5Setup() run the AID batch, immediately
     *  before SetupPod (0x03, [app.aaps.pump.omnipod.common.bledriver.pod.command
     *  .SetUniqueIdCommand] here). Must come after [GOT_POD_VERSION], not before it - an
     *  earlier version of this code had it reversed. Dash's activation flow skips straight
     *  past this state (it has no such requirement), so it costs Dash nothing to have this
     *  ordinal exist. */
    AID_SETUP,
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
