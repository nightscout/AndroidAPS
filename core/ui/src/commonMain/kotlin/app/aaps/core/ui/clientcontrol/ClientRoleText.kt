package app.aaps.core.ui.clientcontrol

import app.aaps.core.keys.interfaces.ClientRole
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings

/** The short label of a [ClientRole], shared by the master's Authorized clients screen and the client's pairing screen. */
fun ClientRole.label(): TextRef = when (this) {
    ClientRole.ReadOnly      -> CoreUiStrings.client_role_read_only
    ClientRole.MealAssistant -> CoreUiStrings.client_role_meal_assistant
    ClientRole.Caregiver     -> CoreUiStrings.client_role_caregiver
    ClientRole.Admin         -> CoreUiStrings.client_role_admin
}
