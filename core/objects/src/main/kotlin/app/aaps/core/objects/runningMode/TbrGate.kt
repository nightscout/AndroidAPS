package app.aaps.core.objects.runningMode

import app.aaps.core.data.model.RM

/**
 * Gate that decides whether a given pump command is allowed for the currently active running mode.
 *
 * Complements the running-mode reconciler in plugins:aps: the reconciler enforces that the pump
 * reaches the claimed state; the gate ensures other sources cannot push the pump into a state
 * that contradicts the mode.
 *
 * cancelTempBasal is always allowed — it is the primitive needed to return to base basal during
 * RESUME and is safe in every mode.
 *
 * **Last-resort safety net.** When the queue-level gate check in CommandQueueImplementation
 * rejects a command, the caller's failure callback fires — which in many call sites triggers
 * the "treatment delivery error" alarm. That alarm is appropriate for real pump failures, not
 * for commands blocked by mode policy.
 *
 * Primary defense belongs at the entry points (UI dialogs, SMS handler, Wear handler, Garmin,
 * automation): use [RunningModeGuard] before calling CommandQueue so rejections surface as a
 * quiet snackbar / reply and never reach the queue. The queue-level gate catches anything that
 * slips past those checks.
 */
object TbrGate {

    enum class CommandKind {
        /** Absolute rate > 0 or percent != 0 / != 100 in some drivers; any "active" TBR. */
        TEMP_BASAL_NONZERO,

        /** Absolute rate == 0.0 or percent == 0. */
        TEMP_BASAL_ZERO,
        CANCEL_TEMP_BASAL,
        BOLUS,
        EXTENDED_BOLUS
    }

    sealed interface Decision {
        data object Allow : Decision
        data class Reject(val reason: Reason) : Decision
    }

    enum class Reason {
        PUMP_DISCONNECTED,
        LOOP_SUSPENDED_USER,
        LOOP_SUSPENDED_DST,
        PUMP_REPORTED_SUSPENDED,
        SUPER_BOLUS_ACTIVE
    }

    fun check(mode: RM.Mode, kind: CommandKind): Decision =
        when (mode) {
            RM.Mode.OPEN_LOOP,
            RM.Mode.CLOSED_LOOP,
            RM.Mode.CLOSED_LOOP_LGS,
            RM.Mode.DISABLED_LOOP,
            RM.Mode.RESUME            -> Decision.Allow

            RM.Mode.DISCONNECTED_PUMP -> when (kind) {
                CommandKind.CANCEL_TEMP_BASAL,
                CommandKind.TEMP_BASAL_ZERO -> Decision.Allow

                CommandKind.TEMP_BASAL_NONZERO,
                CommandKind.BOLUS,
                CommandKind.EXTENDED_BOLUS  -> Decision.Reject(Reason.PUMP_DISCONNECTED)
            }

            RM.Mode.SUPER_BOLUS       -> when (kind) {
                CommandKind.CANCEL_TEMP_BASAL,
                CommandKind.TEMP_BASAL_ZERO,
                CommandKind.BOLUS          -> Decision.Allow

                CommandKind.TEMP_BASAL_NONZERO,
                CommandKind.EXTENDED_BOLUS -> Decision.Reject(Reason.SUPER_BOLUS_ACTIVE)
            }

            RM.Mode.SUSPENDED_BY_USER -> when (kind) {
                CommandKind.CANCEL_TEMP_BASAL -> Decision.Allow
                else                          -> Decision.Reject(Reason.LOOP_SUSPENDED_USER)
            }

            RM.Mode.SUSPENDED_BY_DST  -> when (kind) {
                CommandKind.CANCEL_TEMP_BASAL -> Decision.Allow
                else                          -> Decision.Reject(Reason.LOOP_SUSPENDED_DST)
            }

            RM.Mode.SUSPENDED_BY_PUMP -> when (kind) {
                CommandKind.CANCEL_TEMP_BASAL -> Decision.Allow
                else                          -> Decision.Reject(Reason.PUMP_REPORTED_SUSPENDED)
            }
        }
}
