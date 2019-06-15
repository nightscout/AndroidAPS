package info.nightscout.androidaps.plugins.pump.medtronic.service;

import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.os.Binder;
import android.os.IBinder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.logging.L;
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
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks.ServiceTask;
import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil;
import info.nightscout.androidaps.plugins.pump.medtronic.MedtronicPumpPlugin;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.MedtronicCommunicationManager;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.PumpDeviceState;
import info.nightscout.androidaps.plugins.pump.medtronic.driver.MedtronicPumpStatus;
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedtronicConst;
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedtronicUtil;
import info.nightscout.androidaps.utils.SP;

/**
 * RileyLinkMedtronicService is intended to stay running when the gui-app is closed.
 */
public class RileyLinkMedtronicService extends RileyLinkService {

    private static final Logger LOG = LoggerFactory.getLogger(L.PUMPCOMM);

    private static RileyLinkMedtronicService instance;
    private static ServiceTask currentTask = null;

    // cache of most recently received set of pump history pages. Probably shouldn't be here.
    public MedtronicCommunicationManager medtronicCommunicationManager;
    MedtronicPumpStatus pumpStatus = null;
    private IBinder mBinder = new LocalBinder();


    public RileyLinkMedtronicService() {
        super(MainApp.instance().getApplicationContext());
        instance = this;
        if (isLogEnabled())
            LOG.debug("RileyLinkMedtronicService newly constructed");
        MedtronicUtil.setMedtronicService(this);
        pumpStatus = (MedtronicPumpStatus) MedtronicPumpPlugin.getPlugin().getPumpStatusData();
    }


    public static RileyLinkMedtronicService getInstance() {
        return instance;
    }


    public static MedtronicCommunicationManager getCommunicationManager() {
        return instance.medtronicCommunicationManager;
    }


    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        if (isLogEnabled())
            LOG.warn("onConfigurationChanged");
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

        RileyLinkUtil.setRileyLinkServiceData(rileyLinkServiceData);
        RileyLinkUtil.setTargetDevice(RileyLinkTargetDevice.MedtronicPump);

        setPumpIDString(SP.getString(MedtronicConst.Prefs.PumpSerial, "000000"));

        // get most recently used RileyLink address
        rileyLinkServiceData.rileylinkAddress = SP.getString(RileyLinkConst.Prefs.RileyLinkAddress, "");

        rileyLinkBLE = new RileyLinkBLE(this.context); // or this
        rfspy = new RFSpy(rileyLinkBLE);
        rfspy.startReader();

        RileyLinkUtil.setRileyLinkBLE(rileyLinkBLE);

        // init rileyLinkCommunicationManager
        medtronicCommunicationManager = new MedtronicCommunicationManager(context, rfspy);
    }


    public void resetRileyLinkConfiguration() {
        rfspy.resetRileyLinkConfiguration();
    }


    @Override
    public RileyLinkCommunicationManager getDeviceCommunicationManager() {
        return this.medtronicCommunicationManager;
    }


    public void setPumpIDString(String pumpID) {
        if (pumpID.length() != 6) {
            LOG.error("setPumpIDString: invalid pump id string: " + pumpID);
            return;
        }

        byte[] pumpIDBytes = ByteUtil.fromHexString(pumpID);

        if (pumpIDBytes == null) {
            LOG.error("Invalid pump ID? - PumpID is null.");

            rileyLinkServiceData.setPumpID("000000", new byte[]{0, 0, 0});

        } else if (pumpIDBytes.length != 3) {
            LOG.error("Invalid pump ID? " + ByteUtil.shortHexString(pumpIDBytes));

            rileyLinkServiceData.setPumpID("000000", new byte[]{0, 0, 0});

        } else if (pumpID.equals("000000")) {
            LOG.error("Using pump ID " + pumpID);

            rileyLinkServiceData.setPumpID(pumpID, new byte[]{0, 0, 0});

        } else {
            LOG.info("Using pump ID " + pumpID);

            String oldId = rileyLinkServiceData.pumpID;

            rileyLinkServiceData.setPumpID(pumpID, pumpIDBytes);

            if (oldId != null && !oldId.equals(pumpID)) {
                MedtronicUtil.setMedtronicPumpModel(null); // if we change pumpId, model probably changed too
            }

            return;
        }

        MedtronicUtil.setPumpDeviceState(PumpDeviceState.InvalidConfiguration);

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
