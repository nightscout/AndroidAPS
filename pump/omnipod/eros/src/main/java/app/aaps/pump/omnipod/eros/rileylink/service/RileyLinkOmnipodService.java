package app.aaps.pump.omnipod.eros.rileylink.service;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Binder;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

import javax.inject.Inject;

import app.aaps.core.interfaces.logging.LTag;
import app.aaps.core.interfaces.pump.defs.PumpDeviceState;
import app.aaps.pump.common.hw.rileylink.RileyLinkCommunicationManager;
import app.aaps.pump.common.hw.rileylink.RileyLinkConst;
import app.aaps.pump.common.hw.rileylink.ble.defs.RileyLinkEncodingType;
import app.aaps.pump.common.hw.rileylink.ble.defs.RileyLinkTargetFrequency;
import app.aaps.pump.common.hw.rileylink.defs.RileyLinkTargetDevice;
import app.aaps.pump.common.hw.rileylink.keys.RileyLinkStringKey;
import app.aaps.pump.common.hw.rileylink.keys.RileyLinkStringPreferenceKey;
import app.aaps.pump.common.hw.rileylink.service.RileyLinkService;
import app.aaps.pump.omnipod.eros.OmnipodErosPumpPlugin;
import app.aaps.pump.omnipod.eros.R;
import app.aaps.pump.omnipod.eros.rileylink.manager.OmnipodRileyLinkCommunicationManager;
import app.aaps.pump.omnipod.eros.util.AapsOmnipodUtil;


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
    @Nullable private String errorDescription;

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

    @NonNull @Override
    public RileyLinkEncodingType getEncoding() {
        return RileyLinkEncodingType.Manchester;
    }

    @Override
    public void initRileyLinkServiceData() {
        rileyLinkServiceData.setTargetDevice(RileyLinkTargetDevice.Omnipod);
        rileyLinkServiceData.setRileyLinkTargetFrequency(RileyLinkTargetFrequency.Omnipod);

        rileyLinkServiceData.setRileyLinkAddress(preferences.get(RileyLinkStringPreferenceKey.MacAddress));
        rileyLinkServiceData.setRileyLinkName(preferences.get(RileyLinkStringKey.Name));

        rfSpy.startReader();

        aapsLogger.debug(LTag.PUMPBTCOMM, "RileyLinkOmnipodService newly constructed");
    }

    @NonNull @Override
    public RileyLinkCommunicationManager getDeviceCommunicationManager() {
        return omnipodRileyLinkCommunicationManager;
    }

    @Override
    public void setPumpDeviceState(@NonNull PumpDeviceState pumpDeviceState) {
        // Intentionally left blank
        // We don't use PumpDeviceState in the Omnipod driver
    }

    @Nullable public String getErrorDescription() {
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

            String rileyLinkAddress = preferences.get(RileyLinkStringPreferenceKey.MacAddress);

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

            rileyLinkServiceData.setRileyLinkTargetFrequency(RileyLinkTargetFrequency.Omnipod);

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
                rileyLinkUtil.sendBroadcastMessage(RileyLinkConst.Intents.RileyLinkNewAddressSet);
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
