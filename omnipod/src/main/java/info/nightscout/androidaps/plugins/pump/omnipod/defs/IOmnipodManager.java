package info.nightscout.androidaps.plugins.pump.omnipod.defs;

import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.plugins.pump.common.data.TempBasalPair;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.podinfo.PodInfoRecentPulseLog;

// TODO remove?
//  We only have this interface for possible Omnipod Dash implementation
public interface IOmnipodManager {

    /**
     * Initialize Pod
     */
    PumpEnactResult initPod(PodInitActionType podInitActionType, PodInitReceiver podInitReceiver, Profile profile);

    /**
     * Get Pod Status (is pod running, battery left ?, reservoir, etc)
     */
    // TODO we should probably return a (wrapped) StatusResponse instead of a PumpEnactResult
    PumpEnactResult getPodStatus();

    /**
     * Deactivate Pod
     */
    PumpEnactResult deactivatePod(PodInitReceiver podInitReceiver);

    /**
     * Set Basal Profile
     */
    PumpEnactResult setBasalProfile(Profile basalProfile);

    /**
     * Reset Pod status (if we forget to disconnect Pod and want to init new pod, and want to forget current pod)
     */
    PumpEnactResult resetPodStatus();

    /**
     * Set Bolus
     *
     * @param detailedBolusInfo DetailedBolusInfo instance with amount and all other required data
     */
    PumpEnactResult setBolus(DetailedBolusInfo detailedBolusInfo);

    /**
     * Cancel Bolus (if bolus is already stopped, return acknowledgment)
     */
    PumpEnactResult cancelBolus();

    /**
     * Set Temporary Basal
     *
     * @param tempBasalPair TempBasalPair object containg amount and duration in minutes
     */
    PumpEnactResult setTemporaryBasal(TempBasalPair tempBasalPair);

    /**
     * Cancel Temporary Basal (if TB is already stopped, return acknowledgment)
     */
    PumpEnactResult cancelTemporaryBasal();

    /**
     * Acknowledge alerts
     */
    PumpEnactResult acknowledgeAlerts();

    /**
     * Set Time on Pod
     */
    PumpEnactResult setTime();

    PodInfoRecentPulseLog readPulseLog();
}
