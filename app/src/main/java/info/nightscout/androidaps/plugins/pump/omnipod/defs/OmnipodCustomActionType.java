package info.nightscout.androidaps.plugins.pump.omnipod.defs;

import info.nightscout.androidaps.plugins.general.actions.defs.CustomActionType;

/**
 * Created by andy on 4.8.2019
 */

public enum OmnipodCustomActionType implements CustomActionType {

    ResetRileyLinkConfiguration(), //
    PairAndPrime(), //
    FillCanulaSetBasalProfile(), //
    //InitPod(), //
    DeactivatePod(), //
    ResetPodStatus(), //
    ;

    @Override
    public String getKey() {
        return this.name();
    }

}
