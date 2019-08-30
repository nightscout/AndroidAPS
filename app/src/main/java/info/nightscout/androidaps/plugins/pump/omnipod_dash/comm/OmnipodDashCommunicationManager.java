package info.nightscout.androidaps.plugins.pump.omnipod_dash.comm;

import android.content.Context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.pump.common.data.TempBasalPair;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkConst;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.RFSpy;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.OmnipodCommunicationManagerInterface;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.state.PodSessionState;
import info.nightscout.androidaps.plugins.pump.omnipod.util.OmnipodUtil;
import info.nightscout.androidaps.utils.SP;

/**
 * Created by andy on 4.8.2019
 */
public class OmnipodDashCommunicationManager implements OmnipodCommunicationManagerInterface {

    private static final Logger LOG = LoggerFactory.getLogger(L.PUMPCOMM);

    private static OmnipodDashCommunicationManager omnipodCommunicationManager;
    String errorMessage;


    public OmnipodDashCommunicationManager(Context context, RFSpy rfspy) {
        omnipodCommunicationManager = this;
        OmnipodUtil.getPumpStatus().previousConnection = SP.getLong(
                RileyLinkConst.Prefs.LastGoodDeviceCommunicationTime, 0L);
    }


    private PodSessionState getPodSessionState() {
        return null;
    }


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


    private boolean isLogEnabled() {
        return L.isEnabled(L.PUMPCOMM);
    }


    // This are just skeleton methods, we need to see what we can get returned and act accordingly
    @Override
    public PumpEnactResult pairAndPrime() {
        //omnipodManager.pairAndPrime();


        return null;
    }

    @Override
    public PumpEnactResult insertCannula(Profile basalProfile) {
        return null;
    }

    @Override
    public PumpEnactResult getPodStatus() {
        return null;
    }

    @Override
    public PumpEnactResult deactivatePod() {
        return null;
    }

    @Override
    public PumpEnactResult setBasalProfile(Profile basalProfile) {
        return null;
    }

    @Override
    public PumpEnactResult resetPodState() {
        return null;
    }

    @Override
    public PumpEnactResult bolus(Double parameter) {
        return null;
    }

    @Override
    public PumpEnactResult cancelBolus() {
        return null;
    }

    @Override
    public PumpEnactResult setTemporaryBasal(TempBasalPair tempBasalPair) {
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
}
