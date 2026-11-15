package app.aaps.core.interfaces.bolus

import app.aaps.core.keys.interfaces.ClientRole

/**
 * The paired client that asked the master for an action. Null where the master is asked by itself (its own
 * screens, the watch, SMS, Garmin).
 *
 * A prepared action is kept for its caller only: a commit from anyone else finds nothing. The caller's role is
 * checked again at commit, so a role that was lowered in between is honoured.
 */
data class RemoteCaller(val clientId: String, val role: ClientRole)

/**
 * The lowest client role that may run this action: insulin and carbs ([BatchAction.Bolus], eCarbs and a
 * record-only bolus included) are for a meal assistant, everything else needs a caregiver.
 */
val BatchAction.requiredRole: ClientRole
    get() = when (this) {
        is BatchAction.Bolus -> ClientRole.MealAssistant
        else                 -> ClientRole.Caregiver
    }

/** A batch needs the highest role of its actions, so a harmless action cannot carry a stronger one past the role. */
val List<BatchAction>.requiredRole: ClientRole
    get() = maxOfOrNull { it.requiredRole } ?: ClientRole.MealAssistant
