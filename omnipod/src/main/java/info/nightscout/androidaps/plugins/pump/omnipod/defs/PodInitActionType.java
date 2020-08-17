package info.nightscout.androidaps.plugins.pump.omnipod.defs;

import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.plugins.pump.omnipod.R;

public enum PodInitActionType {

    PairAndPrimeWizardStep(), //
    PairPod(R.string.omnipod_init_pod_pair_pod, PairAndPrimeWizardStep), //
    PrimePod(R.string.omnipod_init_pod_prime_pod, PairAndPrimeWizardStep), //

    FillCannulaSetBasalProfileWizardStep(), //
    FillCannula(R.string.omnipod_init_pod_fill_cannula, FillCannulaSetBasalProfileWizardStep), //
    SetBasalProfile(R.string.omnipod_init_pod_set_basal_profile, FillCannulaSetBasalProfileWizardStep), //

    DeactivatePodWizardStep(), //
    CancelDelivery(R.string.omnipod_deactivate_pod_cancel_delivery, DeactivatePodWizardStep), //
    DeactivatePod(R.string.omnipod_deactivate_pod_deactivate_pod, DeactivatePodWizardStep);

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
