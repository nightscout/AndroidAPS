package info.nightscout.androidaps.plugins.pump.omnipod.definition;

import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.plugins.pump.omnipod.R;

public enum PodInitActionType {

    PAIR_AND_PRIME_WIZARD_STEP(), //
    PAIR_POD(R.string.omnipod_init_pod_pair_pod, PAIR_AND_PRIME_WIZARD_STEP), //
    PRIME_POD(R.string.omnipod_init_pod_prime_pod, PAIR_AND_PRIME_WIZARD_STEP), //

    FILL_CANNULA_SET_BASAL_PROFILE_WIZARD_STEP(), //
    FILL_CANNULA(R.string.omnipod_init_pod_fill_cannula, FILL_CANNULA_SET_BASAL_PROFILE_WIZARD_STEP), //
    SET_BASAL_PROFILE(R.string.omnipod_init_pod_set_basal_profile, FILL_CANNULA_SET_BASAL_PROFILE_WIZARD_STEP), //

    DEACTIVATE_POD_WIZARD_STEP(), //
    CANCEL_DELIVERY(R.string.omnipod_deactivate_pod_cancel_delivery, DEACTIVATE_POD_WIZARD_STEP), //
    DEACTIVATE_POD(R.string.omnipod_deactivate_pod_deactivate_pod, DEACTIVATE_POD_WIZARD_STEP);

    private int resourceId;
    private PodInitActionType parent;

    PodInitActionType(int resourceId, PodInitActionType parent) {
        this.resourceId = resourceId;
        this.parent = parent;
    }

    PodInitActionType() {
    }

    public boolean isParent() {
        return this.parent == null;
    }

    public List<PodInitActionType> getChildren() {

        List<PodInitActionType> outList = new ArrayList<>();

        for (PodInitActionType value : values()) {
            if (value.parent == this) {
                outList.add(value);
            }
        }

        return outList;
    }

    public int getResourceId() {
        return resourceId;
    }

}
