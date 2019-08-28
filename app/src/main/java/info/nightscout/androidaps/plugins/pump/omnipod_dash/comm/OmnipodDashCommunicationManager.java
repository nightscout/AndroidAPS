package info.nightscout.androidaps.plugins.pump.omnipod_dash.comm;

import android.content.Context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.pump.common.data.TempBasalPair;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkConst;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.RFSpy;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.data.PodCommResponse;
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
    public PodCommResponse pairAndPrime() {
        //omnipodManager.pairAndPrime();


        return null;
    }

    @Override
    public PodCommResponse insertCannula(Profile basalProfile) {
        return null;
    }

    @Override
    public PodCommResponse getPodStatus() {
        return null;
    }

    @Override
    public PodCommResponse deactivatePod() {
        return null;
    }

    @Override
    public PodCommResponse setBasalProfile(Profile basalProfile) {
        return null;
    }

    @Override
    public PodCommResponse resetPodState() {
        return null;
    }

    @Override
    public PodCommResponse bolus(Double parameter) {
        return null;
    }

    @Override
    public PodCommResponse cancelBolus() {
        return null;
    }

    @Override
    public PodCommResponse setTemporaryBasal(TempBasalPair tempBasalPair) {
        return null;
    }

    @Override
    public PodCommResponse cancelTemporaryBasal() {
        return null;
    }

    @Override
    public PodCommResponse acknowledgeAlerts() {
        return null;
    }
}
