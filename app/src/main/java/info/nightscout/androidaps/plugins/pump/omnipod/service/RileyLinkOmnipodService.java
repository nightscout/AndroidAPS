package info.nightscout.androidaps.plugins.pump.omnipod.service;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.os.Binder;
import android.os.IBinder;

import com.google.gson.Gson;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkCommunicationManager;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkConst;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkUtil;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.RFSpy;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.RileyLinkBLE;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RileyLinkEncodingType;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkServiceState;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkTargetDevice;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.RileyLinkService;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.RileyLinkServiceData;
import info.nightscout.androidaps.plugins.pump.medtronic.MedtronicPumpPlugin;
import info.nightscout.androidaps.plugins.pump.omnipod.OmnipodPumpPlugin;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.OmnipodCommunicationService;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.OmnipodCommunicationManagerInterface;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.state.PodSessionState;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.OmnipodPumpStatus;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.comm.AapsOmnipodManager;
import info.nightscout.androidaps.plugins.pump.omnipod.util.OmnipodConst;
import info.nightscout.androidaps.plugins.pump.omnipod.util.OmnipodUtil;
import info.nightscout.androidaps.utils.sharedPreferences.SP;


/**
 * Created by andy on 4.8.2019
 * RileyLinkOmnipodService is intended to stay running when the gui-app is closed.
 */
public class RileyLinkOmnipodService extends RileyLinkService {

    @Inject AAPSLogger aapsLogger;
    @Inject Context context;
    @Inject OmnipodPumpPlugin omnipodPumpPlugin;
    @Inject SP sp;
    
    //private static final Logger LOG = LoggerFactory.getLogger(L.PUMPCOMM);

    private static RileyLinkOmnipodService instance;

    OmnipodCommunicationManagerInterface omnipodCommunicationManager;
    OmnipodPumpStatus pumpStatus = null;
    private IBinder mBinder = new LocalBinder();


    public RileyLinkOmnipodService() {
        super();
        instance = this;
        if (isLogEnabled())
            aapsLogger.debug(LTag.PUMPCOMM,"RileyLinkOmnipodService newly constructed");
        OmnipodUtil.setOmnipodService(this);
        pumpStatus = (OmnipodPumpStatus) OmnipodPumpPlugin.getPlugin().getPumpStatusData();
        //aapsLogger.debug(LTag.PUMPCOMM,"RRRRRRRRRR: " + pumpStatus);
    }


    public static RileyLinkOmnipodService getInstance() {
        return instance;
    }


//    public static MedtronicCommunicationManager getCommunicationManager() {
//        return instance.medtronicCommunicationManager;
//    }


    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        if (isLogEnabled())
            aapsLogger.warn(LTag.PUMPCOMM,"onConfigurationChanged");
        super.onConfigurationChanged(newConfig);
    }


    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }


    @Override
    public RileyLinkEncodingType getEncoding() {
        return RileyLinkEncodingType.Manchester;
    }


    /**
     * If you have customized RileyLinkServiceData you need to override this
     */
    public void initRileyLinkServiceData() {

        rileyLinkServiceData = new RileyLinkServiceData(RileyLinkTargetDevice.Omnipod);

        RileyLinkUtil.setRileyLinkServiceData(rileyLinkServiceData);
        RileyLinkUtil.setTargetDevice(RileyLinkTargetDevice.Omnipod);

        // get most recently used RileyLink address
        rileyLinkServiceData.rileylinkAddress = sp.getString(RileyLinkConst.Prefs.RileyLinkAddress, "");

        rileyLinkBLE = new RileyLinkBLE(this.context); // or this
        rfspy = new RFSpy(rileyLinkBLE);
        rfspy.startReader();

        RileyLinkUtil.setRileyLinkBLE(rileyLinkBLE);

        // init rileyLinkCommunicationManager
        initializeErosOmnipodManager();
        // TODO Dash
    }

    private void initializeErosOmnipodManager() {
        if (AapsOmnipodManager.getInstance() == null) {
            PodSessionState podState = null;
            if (sp.contains(OmnipodConst.Prefs.PodState)) {
                try {
                    Gson gson = OmnipodUtil.getGsonInstance();
                    String storedPodState = sp.getString(OmnipodConst.Prefs.PodState, null);
                    aapsLogger.info(LTag.PUMPCOMM,"PodSessionState-SP: loaded from SharedPreferences: " + storedPodState);
                    podState = gson.fromJson(storedPodState, PodSessionState.class);
                    OmnipodUtil.setPodSessionState(podState);
                } catch (Exception ex) {
                    aapsLogger.error(LTag.PUMPCOMM,"Could not deserialize Pod state", ex);
                }
            }
            OmnipodCommunicationService omnipodCommunicationService = new OmnipodCommunicationService(rfspy);
            omnipodCommunicationService.setPumpStatus(pumpStatus);

            omnipodCommunicationManager = new AapsOmnipodManager(omnipodCommunicationService, podState, pumpStatus);
        } else {
            omnipodCommunicationManager = AapsOmnipodManager.getInstance();
        }
    }


    public void resetRileyLinkConfiguration() {
        rfspy.resetRileyLinkConfiguration();
    }


    @Override
    public RileyLinkCommunicationManager getDeviceCommunicationManager() {
        if (omnipodCommunicationManager instanceof AapsOmnipodManager) { // Eros
            return ((AapsOmnipodManager) omnipodCommunicationManager).getCommunicationService();
        }
        // FIXME is this correct for Dash?
        return (RileyLinkCommunicationManager) omnipodCommunicationManager;
    }


    public class LocalBinder extends Binder {

        public RileyLinkOmnipodService getServiceInstance() {
            return RileyLinkOmnipodService.this;
        }
    }


    /* private functions */

    // PumpInterface - REMOVE

    public boolean isInitialized() {
        return RileyLinkServiceState.isReady(RileyLinkUtil.getRileyLinkServiceData().serviceState);
    }


    @Override
    public String getDeviceSpecificBroadcastsIdentifierPrefix() {
        return null;
    }


    public boolean handleDeviceSpecificBroadcasts(Intent intent) {
        return false;
    }


    @Override
    public void registerDeviceSpecificBroadcasts(IntentFilter intentFilter) {
    }


    private boolean isLogEnabled() {
        return L.isEnabled(L.PUMPCOMM);
    }
}
