package info.nightscout.androidaps.plugins.pump.medtronic.defs

import app.aaps.core.interfaces.pump.actions.CustomActionType

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