package info.nightscout.androidaps.plugins.pump.medtronic.service;

import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.os.Binder;
import android.os.IBinder;

import javax.inject.Inject;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkCommunicationManager;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkConst;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.RFSpy;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.RileyLinkBLE;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RileyLinkEncodingType;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkServiceState;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkTargetDevice;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.RileyLinkService;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.RileyLinkServiceData;
import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil;
import info.nightscout.androidaps.plugins.pump.medtronic.MedtronicPumpPlugin;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.MedtronicCommunicationManager;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.ui.MedtronicUIComm;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.ui.MedtronicUIPostprocessor;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.PumpDeviceState;
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedtronicConst;
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedtronicUtil;

/**
 * RileyLinkMedtronicService is intended to stay running when the gui-app is closed.
 */
public class RileyLinkMedtronicService extends RileyLinkService {

    @Inject HasAndroidInjector injector;
    @Inject MedtronicPumpPlugin medtronicPumpPlugin;
    @Inject MedtronicUtil medtronicUtil;
    @Inject MedtronicUIPostprocessor medtronicUIPostprocessor;


    private MedtronicUIComm medtronicUIComm;
    private MedtronicCommunicationManager medtronicCommunicationManager;
    private IBinder mBinder = new LocalBinder();


    public RileyLinkMedtronicService() {
        super();
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
        return RileyLinkEncodingType.FourByteSixByteLocal;
    }


    /**
     * If you have customized RileyLinkServiceData you need to override this
     */
    public void initRileyLinkServiceData() {

        rileyLinkServiceData = new RileyLinkServiceData(RileyLinkTargetDevice.MedtronicPump);

        rileyLinkUtil.setRileyLinkServiceData(rileyLinkServiceData);
        rileyLinkUtil.setTargetDevice(RileyLinkTargetDevice.MedtronicPump);

        setPumpIDString(sp.getString(MedtronicConst.Prefs.PumpSerial, "000000"));

        // get most recently used RileyLink address
        rileyLinkServiceData.rileylinkAddress = sp.getString(RileyLinkConst.Prefs.RileyLinkAddress, "");

        rileyLinkBLE = new RileyLinkBLE(this.context); // or this
        rfspy = new RFSpy(rileyLinkBLE);
        rfspy.startReader();

        rileyLinkUtil.setRileyLinkBLE(rileyLinkBLE);

        // init rileyLinkCommunicationManager
        medtronicCommunicationManager = new MedtronicCommunicationManager(injector, rfspy);
        medtronicUIComm = new MedtronicUIComm(injector, aapsLogger, medtronicUtil, medtronicUIPostprocessor, medtronicCommunicationManager);

        aapsLogger.debug(LTag.PUMPCOMM, "RileyLinkMedtronicService newly constructed");
        medtronicUtil.setMedtronicService(this);
    }


    public void resetRileyLinkConfiguration() {
        rfspy.resetRileyLinkConfiguration();
    }


    public MedtronicCommunicationManager getDeviceCommunicationManager() {
        return this.medtronicCommunicationManager;
    }


    public MedtronicUIComm getMedtronicUIComm() {
        return medtronicUIComm;
    }

    public void setPumpIDString(String pumpID) {
        if (pumpID.length() != 6) {
            aapsLogger.error("setPumpIDString: invalid pump id string: " + pumpID);
            return;
        }

        byte[] pumpIDBytes = ByteUtil.fromHexString(pumpID);

        if (pumpIDBytes == null) {
            aapsLogger.error("Invalid pump ID? - PumpID is null.");

            rileyLinkServiceData.setPumpID("000000", new byte[]{0, 0, 0});

        } else if (pumpIDBytes.length != 3) {
            aapsLogger.error("Invalid pump ID? " + ByteUtil.shortHexString(pumpIDBytes));

            rileyLinkServiceData.setPumpID("000000", new byte[]{0, 0, 0});

        } else if (pumpID.equals("000000")) {
            aapsLogger.error("Using pump ID " + pumpID);

            rileyLinkServiceData.setPumpID(pumpID, new byte[]{0, 0, 0});

        } else {
            aapsLogger.info(LTag.PUMPBTCOMM, "Using pump ID " + pumpID);

            String oldId = rileyLinkServiceData.pumpID;

            rileyLinkServiceData.setPumpID(pumpID, pumpIDBytes);

            if (oldId != null && !oldId.equals(pumpID)) {
                medtronicUtil.setMedtronicPumpModel(null); // if we change pumpId, model probably changed too
            }

            return;
        }

        medtronicUtil.setPumpDeviceState(PumpDeviceState.InvalidConfiguration);

        // LOG.info("setPumpIDString: saved pumpID " + idString);
    }

    public class LocalBinder extends Binder {

        public RileyLinkMedtronicService getServiceInstance() {
            return RileyLinkMedtronicService.this;
        }
    }


    /* private functions */

    // PumpInterface - REMOVE

    public boolean isInitialized() {
        return RileyLinkServiceState.isReady(rileyLinkUtil.getRileyLinkServiceData().serviceState);
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
}
