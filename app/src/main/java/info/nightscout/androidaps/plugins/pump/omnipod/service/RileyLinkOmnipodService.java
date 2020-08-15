package info.nightscout.androidaps.plugins.pump.omnipod.service;

import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.os.Binder;
import android.os.IBinder;

import com.google.gson.Gson;

import org.apache.commons.lang3.StringUtils;

import javax.inject.Inject;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpDeviceState;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkCommunicationManager;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkConst;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.RFSpy;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.RileyLinkBLE;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RileyLinkEncodingType;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RileyLinkTargetFrequency;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkServiceState;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkTargetDevice;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.RileyLinkService;
import info.nightscout.androidaps.plugins.pump.omnipod.OmnipodPumpPlugin;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.OmnipodCommunicationManager;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.state.PodSessionState;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.OmnipodPumpStatus;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.comm.AapsOmnipodManager;
import info.nightscout.androidaps.plugins.pump.omnipod.util.OmnipodConst;
import info.nightscout.androidaps.plugins.pump.omnipod.util.OmnipodUtil;


/**
 * Created by andy on 4.8.2019
 * RileyLinkOmnipodService is intended to stay running when the gui-app is closed.
 */
public class RileyLinkOmnipodService extends RileyLinkService {

    @Inject OmnipodPumpPlugin omnipodPumpPlugin;
    @Inject OmnipodPumpStatus omnipodPumpStatus;
    @Inject OmnipodUtil omnipodUtil;

    private static RileyLinkOmnipodService instance;

    private OmnipodCommunicationManager omnipodCommunicationManager;
    private AapsOmnipodManager aapsOmnipodManager;

    private IBinder mBinder = new LocalBinder();
    private boolean rileyLinkAddressChanged = false;
    private boolean inPreInit = true;


    public RileyLinkOmnipodService() {
        super();
        instance = this;
    }


    public static RileyLinkOmnipodService getInstance() {
        return instance;
    }


    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        aapsLogger.warn(LTag.PUMPCOMM, "onConfigurationChanged");
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

        rileyLinkServiceData.targetDevice = RileyLinkTargetDevice.Omnipod;
        rileyLinkServiceData.rileyLinkTargetFrequency = RileyLinkTargetFrequency.Omnipod;

        // get most recently used RileyLink address
        rileyLinkServiceData.rileylinkAddress = sp.getString(RileyLinkConst.Prefs.RileyLinkAddress, "");

        rileyLinkBLE = new RileyLinkBLE(injector, this); // or this
        rfspy = new RFSpy(injector, rileyLinkBLE);
        rfspy.startReader();

        initializeErosOmnipodManager();

        aapsLogger.debug(LTag.PUMPCOMM, "RileyLinkOmnipodService newly constructed");
        //omnipodPumpStatus = (OmnipodPumpStatus) omnipodPumpPlugin.getPumpStatusData();
    }

    private void initializeErosOmnipodManager() {
        if (AapsOmnipodManager.getInstance() == null) {
            PodSessionState podState = null;
            if (sp.contains(OmnipodConst.Prefs.PodState) && omnipodUtil.getPodSessionState() == null) {
                try {
                    Gson gson = omnipodUtil.getGsonInstance();
                    String storedPodState = sp.getString(OmnipodConst.Prefs.PodState, "");
                    aapsLogger.info(LTag.PUMPCOMM, "PodSessionState-SP: loaded from SharedPreferences: " + storedPodState);
                    podState = gson.fromJson(storedPodState, PodSessionState.class);
                    podState.injectDaggerClass(injector);
                    omnipodUtil.setPodSessionState(podState);
                } catch (Exception ex) {
                    aapsLogger.error(LTag.PUMPCOMM, "Could not deserialize Pod state", ex);
                }
            }
            OmnipodCommunicationManager omnipodCommunicationService = new OmnipodCommunicationManager(injector, rfspy);
            //omnipodCommunicationService.setPumpStatus(omnipodPumpStatus);
            this.omnipodCommunicationManager = omnipodCommunicationService;

            this.aapsOmnipodManager = new AapsOmnipodManager(omnipodCommunicationService, podState, omnipodPumpStatus,
                    omnipodUtil, aapsLogger, rxBus, sp, resourceHelper, injector, activePlugin, this);
        } else {
            aapsOmnipodManager = AapsOmnipodManager.getInstance();
        }
    }


    public void resetRileyLinkConfiguration() {
        rfspy.resetRileyLinkConfiguration();
    }


    @Override
    public RileyLinkCommunicationManager getDeviceCommunicationManager() {
        return omnipodCommunicationManager;
    }

    @Override
    public void setPumpDeviceState(PumpDeviceState pumpDeviceState) {
        this.omnipodPumpStatus.setPumpDeviceState(pumpDeviceState);
    }


    public class LocalBinder extends Binder {

        public RileyLinkOmnipodService getServiceInstance() {
            return RileyLinkOmnipodService.this;
        }
    }


    /* private functions */

    // PumpInterface - REMOVE

    public boolean isInitialized() {
        return RileyLinkServiceState.isReady(rileyLinkServiceData.rileyLinkServiceState);
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


    public boolean verifyConfiguration() {
        try {
            omnipodPumpStatus.errorDescription = "-";

            String rileyLinkAddress = sp.getString(RileyLinkConst.Prefs.RileyLinkAddress, "");

            if (StringUtils.isEmpty(rileyLinkAddress)) {
                aapsLogger.debug(LTag.PUMPCOMM, "RileyLink address invalid: no address");
                omnipodPumpStatus.errorDescription = resourceHelper.gs(R.string.medtronic_error_rileylink_address_invalid);
                return false;
            } else {
                if (!rileyLinkAddress.matches(omnipodPumpStatus.regexMac)) {
                    omnipodPumpStatus.errorDescription = resourceHelper.gs(R.string.medtronic_error_rileylink_address_invalid);
                    aapsLogger.debug(LTag.PUMPCOMM, "RileyLink address invalid: {}", rileyLinkAddress);
                } else {
                    if (!rileyLinkAddress.equals(this.omnipodPumpStatus.rileyLinkAddress)) {
                        this.omnipodPumpStatus.rileyLinkAddress = rileyLinkAddress;
                        rileyLinkAddressChanged = true;
                    }
                }
            }

            this.omnipodPumpStatus.beepBasalEnabled = sp.getBoolean(OmnipodConst.Prefs.BeepBasalEnabled, true);
            this.omnipodPumpStatus.beepBolusEnabled = sp.getBoolean(OmnipodConst.Prefs.BeepBolusEnabled, true);
            this.omnipodPumpStatus.beepSMBEnabled = sp.getBoolean(OmnipodConst.Prefs.BeepSMBEnabled, true);
            this.omnipodPumpStatus.beepTBREnabled = sp.getBoolean(OmnipodConst.Prefs.BeepTBREnabled, true);
            this.omnipodPumpStatus.podDebuggingOptionsEnabled = sp.getBoolean(OmnipodConst.Prefs.PodDebuggingOptionsEnabled, false);
            this.omnipodPumpStatus.timeChangeEventEnabled = sp.getBoolean(OmnipodConst.Prefs.TimeChangeEventEnabled, true);
            rileyLinkServiceData.rileyLinkTargetFrequency = RileyLinkTargetFrequency.Omnipod;

            aapsLogger.debug(LTag.PUMPCOMM, "Beeps [basal={}, bolus={}, SMB={}, TBR={}]", this.omnipodPumpStatus.beepBasalEnabled, this.omnipodPumpStatus.beepBolusEnabled, this.omnipodPumpStatus.beepSMBEnabled, this.omnipodPumpStatus.beepTBREnabled);

            reconfigureService();

            return true;

        } catch (Exception ex) {
            this.omnipodPumpStatus.errorDescription = ex.getMessage();
            aapsLogger.error(LTag.PUMPCOMM, "Error on Verification: " + ex.getMessage(), ex);
            return false;
        }
    }


    private boolean reconfigureService() {

        if (!inPreInit) {

            if (rileyLinkAddressChanged) {
                rileyLinkUtil.sendBroadcastMessage(RileyLinkConst.Intents.RileyLinkNewAddressSet, this);
                rileyLinkAddressChanged = false;
            }
        }

        return (!rileyLinkAddressChanged);
    }

    public boolean setNotInPreInit() {
        this.inPreInit = false;

        return reconfigureService();
    }
}
