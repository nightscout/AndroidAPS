package info.nightscout.androidaps.plugins.pump.omnipod.defs;

import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.plugins.pump.common.data.TempBasalPair;

public interface OmnipodCommunicationManagerInterface {

    // TODO add methods that can be used by OmniPod Eros and Omnipod Dash

    /**
     * Initialize Pod
     */
    PumpEnactResult initPod(PodInitActionType podInitActionType, PodInitReceiver podIniReceiver);

    /**
     * Get Pod Status (is pod running, battery left ?, reservoir, etc)
     */
    PumpEnactResult getPodStatus();

    /**
     * Deactivate Pod
     */
    PumpEnactResult deactivatePod();

    /**
     * Set Basal Profile
     */
    PumpEnactResult setBasalProfile(Profile profile);

    /**
     * Reset Pod status (if we forget to disconnect Pod and want to init new pod, and want to forget current pod)
     */
    PumpEnactResult resetPodStatus();

    /**
     * Set Bolus
     *
     * @param amount amount of bolus in U
     */
    PumpEnactResult setBolus(Double amount);

    /**
     * Cancel Bolus (if bolus is already stopped, return acknowledgment)
     */
    PumpEnactResult cancelBolus();

    /**
     * Set Temporary Basal
     *
     * @param tbr TempBasalPair object containing amount and duration in minutes
     */
    PumpEnactResult setTemporaryBasal(TempBasalPair tbr);

    /**
     * Cancel Temporary Basal (if TB is already stopped, return acknowledgment)
     */
    PumpEnactResult cancelTemporaryBasal();

    /**
     * Acknowledge alerts
     */
    PumpEnactResult acknowledgeAlerts();


}
