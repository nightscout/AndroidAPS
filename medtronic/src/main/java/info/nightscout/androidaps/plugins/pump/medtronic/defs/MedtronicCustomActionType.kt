package info.nightscout.androidaps.plugins.pump.medtronic.defs

import info.nightscout.androidaps.plugins.general.actions.defs.CustomActionType

/**
 * Created by andy on 11/3/18.
 */
enum class MedtronicCustomActionType : CustomActionType {

    WakeUpAndTune,  //
    ClearBolusBlock,  //
    ResetRileyLinkConfiguration;

    //

    override fun getKey(): String {
        return name
    }
}