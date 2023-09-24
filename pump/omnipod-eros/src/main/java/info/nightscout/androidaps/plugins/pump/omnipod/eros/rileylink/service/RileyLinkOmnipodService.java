package info.nightscout.androidaps.plugins.pump.omnipod.eros.rileylink.service;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Binder;
import android.os.IBinder;

import org.apache.commons.lang3.StringUtils;

import javax.inject.Inject;

import app.aaps.core.interfaces.logging.LTag;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkCommunicationManager;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkConst;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RileyLinkEncodingType;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RileyLinkTargetFrequency;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkTargetDevice;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.RileyLinkService;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.OmnipodErosPumpPlugin;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.R;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.rileylink.manager.OmnipodRileyLinkCommunicationManager;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.util.AapsOmnipodUtil;
import info.nightscout.pump.common.defs.PumpDeviceState;


/**
 * Created by andy on 4.8.2019
 * RileyLinkOmnipodService is intended to stay running when the gui-app is closed.
 */
public class RileyLinkOmnipodService extends RileyLinkService {

    private static final String REGEX_MAC = "([\\da-fA-F]{1,2}(?::|$)){6}";
    private final IBinder mBinder = new LocalBinder();
    @Inject OmnipodErosPumpPlugin omnipodErosPumpPlugin;
    @Inject AapsOmnipodUtil aapsOmnipodUtil;
    @Inject OmnipodRileyLinkCommunicationManager omnipodRileyLinkCommunicationManager;
    private boolean rileyLinkAddressChanged = false;
    private boolean inPreInit = true;
    private String rileyLinkAddress;
    private String errorDescription;

    public RileyLinkOmnipodService() {
        super();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        aapsLogger.warn(LTag.PUMPBTCOMM, "onConfigurationChanged");
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

    @Override
    public void initRileyLinkServiceData() {
        rileyLinkServiceData.targetDevice = RileyLinkTargetDevice.Omnipod;
        rileyLinkServiceData.rileyLinkTargetFrequency = RileyLinkTargetFrequency.Omnipod;

        rileyLinkServiceData.rileyLinkAddress = sp.getString(RileyLinkConst.Prefs.RileyLinkAddress, "");
        rileyLinkServiceData.rileyLinkName = sp.getString(RileyLinkConst.Prefs.RileyLinkName, "");

        rfSpy.startReader();

        aapsLogger.debug(LTag.PUMPBTCOMM, "RileyLinkOmnipodService newly constructed");
    }

    @Override
    public RileyLinkCommunicationManager getDeviceCommunicationManager() {
        return omnipodRileyLinkCommunicationManager;
    }

    @Override
    public void setPumpDeviceState(PumpDeviceState pumpDeviceState) {
        // Intentionally left blank
        // We don't use PumpDeviceState in the Omnipod driver
    }

    public String getErrorDescription() {
        return errorDescription;
    }

    public boolean isInitialized() {
        return rileyLinkServiceData.getRileyLinkServiceState().isReady();
    }

    /* private functions */

    @Override
    public boolean verifyConfiguration(boolean forceRileyLinkAddressRenewal) {
        try {
            errorDescription = null;

            String rileyLinkAddress = sp.getString(RileyLinkConst.Prefs.RileyLinkAddress, "");

            if (StringUtils.isEmpty(rileyLinkAddress)) {
                aapsLogger.debug(LTag.PUMPBTCOMM, "RileyLink address invalid: no address");
                errorDescription = rh.gs(R.string.omnipod_eros_error_riley_link_address_invalid);
                return false;
            } else {
                if (!rileyLinkAddress.matches(REGEX_MAC)) {
                    errorDescription = rh.gs(R.string.omnipod_eros_error_riley_link_address_invalid);
                    aapsLogger.debug(LTag.PUMPBTCOMM, "RileyLink address invalid: {}", rileyLinkAddress);
                } else {
                    if (!rileyLinkAddress.equals(this.rileyLinkAddress)) {
                        this.rileyLinkAddress = rileyLinkAddress;
                        rileyLinkAddressChanged = true;
                    }
                }
            }

            rileyLinkServiceData.rileyLinkTargetFrequency = RileyLinkTargetFrequency.Omnipod;

            reconfigureService(forceRileyLinkAddressRenewal);

            return true;

        } catch (Exception ex) {
            errorDescription = ex.getMessage();
            aapsLogger.error(LTag.PUMPBTCOMM, "Error on Verification: " + ex.getMessage(), ex);
            return false;
        }
    }

    private boolean reconfigureService(boolean forceRileyLinkAddressRenewal) {
        if (!inPreInit) {
            if (rileyLinkAddressChanged || forceRileyLinkAddressRenewal) {
                rileyLinkUtil.sendBroadcastMessage(RileyLinkConst.Intents.RileyLinkNewAddressSet, this);
                rileyLinkAddressChanged = false;
            }
        }

        return (!rileyLinkAddressChanged);
    }

    public boolean setNotInPreInit() {
        this.inPreInit = false;

        return reconfigureService(false);
    }

    public class LocalBinder extends Binder {
        public RileyLinkOmnipodService getServiceInstance() {
            return RileyLinkOmnipodService.this;
        }
    }
}
