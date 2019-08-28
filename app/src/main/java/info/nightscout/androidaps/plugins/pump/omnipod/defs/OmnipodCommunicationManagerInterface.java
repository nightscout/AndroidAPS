package info.nightscout.androidaps.plugins.pump.omnipod.defs;

import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.plugins.pump.common.data.TempBasalPair;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.data.PodCommResponse;

public interface OmnipodCommunicationManagerInterface {

    // TODO add methods that can be used by OmniPod Eros and Omnipod Dash

    /**
     * Pair and prime
     */
    PodCommResponse pairAndPrime();

    /**
     * Insert cannula
     */
    PodCommResponse insertCannula(Profile basalProfile);

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
    PodCommResponse setBasalProfile(Profile basalProfile);

    /**
     * Reset Pod state (if we forget to disconnect Pod and want to init new pod, and want to forget current pod)
     */
    PodCommResponse resetPodState();

    /**
     * Set Bolus
     *
     * @param amount amount of bolus in U
     */
    PodCommResponse bolus(Double amount);

    /**
     * Cancel Bolus (if bolus is already stopped, return acknowledgment)
     */
    PodCommResponse cancelBolus();

    /**
     * Set Temporary Basal
     *
     * @param tempBasalPair TempBasalPair object containg amount and duration in minutes
     */
    PodCommResponse setTemporaryBasal(TempBasalPair tempBasalPair);

    /**
     * Cancel Temporary Basal (if TB is already stopped, return acknowledgment)
     */
    PodCommResponse cancelTemporaryBasal();

    /**
     * Acknowledge alerts
     */
    PodCommResponse acknowledgeAlerts();


}
