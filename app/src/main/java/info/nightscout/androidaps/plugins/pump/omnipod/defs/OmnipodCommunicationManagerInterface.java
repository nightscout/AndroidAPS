package info.nightscout.androidaps.plugins.pump.omnipod.defs;

import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.plugins.pump.common.data.TempBasalPair;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.data.PodCommResponse;

public interface OmnipodCommunicationManagerInterface {

    // TODO add methods that can be used by OmniPod Eros and Omnipod Dash

    /**
     * Initialize Pod
     */
    PodCommResponse initPod();

    /**
     * Get Pod Status (is pod running, battery left ?, reservoir, etc)
     */
    PodCommResponse getPodStatus();

    /**
     * Deactivate Pod
     */
    PodCommResponse deactivatePod();

    /**
     * Set Basal Profile
     */
    PodCommResponse setBasalProfile(Profile profile);

    /**
     * Reset Pod status (if we forget to disconnect Pod and want to init new pod, and want to forget current pod)
     */
    PodCommResponse resetPodStatus();

    /**
     * Set Bolus
     *
     * @param amount amount of bolus in U
     */
    PodCommResponse setBolus(Double amount);

    /**
     * Cancel Bolus (if bolus is already stopped, return acknowledgment)
     */
    PodCommResponse cancelBolus();

    /**
     * Set Temporary Basal
     *
     * @param tbr TempBasalPair object containg amount and duration in minutes
     */
    PodCommResponse setTemporaryBasal(TempBasalPair tbr);

    /**
     * Cancel Temporary Basal (if TB is already stopped, return acknowledgment)
     */
    PodCommResponse cancelTemporaryBasal();

    /**
     * Acknowledge alerts
     */
    PodCommResponse acknowledgeAlerts();


}
