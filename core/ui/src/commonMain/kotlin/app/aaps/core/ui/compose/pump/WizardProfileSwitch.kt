package app.aaps.core.ui.compose.pump

import app.aaps.core.data.model.ICfg
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileStore
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventShowDialog
import app.aaps.core.ui.CoreUiStrings

/*
 * A pump wizard switches the profile in two places: the profile gate, and the insulin the user chose.
 * Both switches can be refused, for example for a basal rate the pump cannot take. The wizards only
 * logged that, so the user did not know. These functions say why, in the app dialog, which is shown
 * above the wizard.
 */

/**
 * Activates [profileName] for the profile gate: at 100 %, with no time shift and no end. When the switch
 * is refused, says why and returns false, so the wizard can stay on the gate.
 */
suspend fun activateProfileFromGate(
    profileFunction: ProfileFunction,
    rh: TextResolver,
    rxBus: RxBus,
    profileStore: ProfileStore,
    profileName: String,
    iCfg: ICfg,
    source: Sources,
    timestamp: Long
): Boolean {
    val created = profileFunction.createProfileSwitch(
        profileStore = profileStore,
        profileName = profileName,
        durationInMinutes = 0,
        percentage = 100,
        timeShiftInHours = 0,
        timestamp = timestamp,
        action = Action.PROFILE_SWITCH,
        source = source,
        note = null,
        listValues = listOf(ValueWithUnit.SimpleString(profileName)),
        iCfg = iCfg
    )
    if (created != null) return true
    val reason = profileFunction.profileSwitchRefusal(profileStore, profileName, 0, 100, 0, iCfg) ?: rh.gs(CoreUiStrings.invalid_profile)
    rxBus.send(EventShowDialog.Ok(title = rh.gs(CoreUiStrings.careportal_profileswitch), message = reason))
    return false
}

/**
 * Switches the running profile to the insulin [iCfg] the user chose in the wizard. When the switch is
 * refused, says why and returns false. The previous insulin is then still in use.
 */
suspend fun switchToWizardInsulin(profileFunction: ProfileFunction, rh: TextResolver, rxBus: RxBus, iCfg: ICfg, source: Sources): Boolean {
    if (profileFunction.createProfileSwitchWithNewInsulin(iCfg, source)) return true
    val message = profileFunction.insulinSwitchRefusal(iCfg)?.let { rh.gs(CoreUiStrings.insulin_not_switched_reason, it) }
        ?: rh.gs(CoreUiStrings.insulin_activation_failed)
    rxBus.send(EventShowDialog.Ok(title = rh.gs(CoreUiStrings.activate_insulin), message = message))
    return false
}
