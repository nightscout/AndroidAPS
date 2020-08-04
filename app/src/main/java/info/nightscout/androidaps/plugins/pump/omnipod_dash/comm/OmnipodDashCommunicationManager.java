package info.nightscout.androidaps.plugins.pump.omnipod_dash.comm;

import android.content.Context;

import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.plugins.pump.common.data.TempBasalPair;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.RFSpy;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.podinfo.PodInfoRecentPulseLog;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.OmnipodCommunicationManagerInterface;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.PodInitActionType;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.PodInitReceiver;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.state.PodSessionState;

/**
 * Created by andy on 4.8.2019
 */
// TODO refactor to use dagger, just commented out errors
// TODO is this class used? remove if not
public class OmnipodDashCommunicationManager implements OmnipodCommunicationManagerInterface {

    // TODO Dagger

//    private static final Logger LOG = LoggerFactory.getLogger(L.PUMPCOMM);

    // i didn't find where you instantiate this
    private static OmnipodDashCommunicationManager omnipodCommunicationManager;
    private String errorMessage;


    public OmnipodDashCommunicationManager(Context context, RFSpy rfspy) {
        omnipodCommunicationManager = this;
//        OmnipodUtil.getPumpStatus().previousConnection = SP.getLong(
//                RileyLinkConst.Prefs.LastGoodDeviceCommunicationTime, 0L);
    }

    private PodSessionState getPodSessionState() {
        return null;
    }

    @Deprecated
    public static OmnipodDashCommunicationManager getInstance() {
        return omnipodCommunicationManager;
    }

    //@Override
    protected void configurePumpSpecificSettings() {
        //pumpStatus = OmnipodUtil.getPumpStatus();
    }

    public String getErrorResponse() {
        return this.errorMessage;
    }

//    private boolean isLogEnabled() {
//        return L.isEnabled(L.PUMPCOMM);
//    }

    @Override
    public PumpEnactResult initPod(PodInitActionType podInitActionType, PodInitReceiver podInitReceiver, Profile profile) {
        return null;
    }

    @Override
    public PumpEnactResult getPodStatus() {
        return null;
    }

    @Override
    public PumpEnactResult deactivatePod(PodInitReceiver podInitReceiver) {
        return null;
    }

    @Override
    public PumpEnactResult setBasalProfile(Profile profile) {
        return null;
    }

    @Override
    public PumpEnactResult resetPodStatus() {
        return null;
    }

    @Override
    public PumpEnactResult setBolus(DetailedBolusInfo detailedBolusInfo) {
        return null;
    }

    public PumpEnactResult setBolus(Double parameter, boolean isSmb) {
        return null;
    }

    @Override
    public PumpEnactResult cancelBolus() {
        return null;
    }

    @Override
    public PumpEnactResult setTemporaryBasal(TempBasalPair tbr) {
        return null;
    }

    @Override
    public PumpEnactResult cancelTemporaryBasal() {
        return null;
    }

    @Override
    public PumpEnactResult acknowledgeAlerts() {
        return null;
    }

    @Override
    public PumpEnactResult setTime() {
        return null;
    }

    @Override
    public PodInfoRecentPulseLog readPulseLog() {
        return null;
    }

}
