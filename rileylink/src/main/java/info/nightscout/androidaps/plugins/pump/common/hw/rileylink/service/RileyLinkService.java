package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;

import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;

import dagger.android.DaggerService;
import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.interfaces.ActivePluginProvider;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpDeviceState;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkCommunicationManager;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkConst;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkUtil;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.RFSpy;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.RileyLinkBLE;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RileyLinkEncodingType;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkError;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkServiceState;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkTargetDevice;
import info.nightscout.androidaps.utils.resources.ResourceHelper;
import info.nightscout.androidaps.utils.sharedPreferences.SP;

/**
 * Created by andy on 5/6/18.
 * Split from original file and renamed.
 */
public abstract class RileyLinkService extends DaggerService {

    @Inject protected AAPSLogger aapsLogger;
    @Inject protected SP sp;
    @Inject protected Context context;
    @Inject protected RxBusWrapper rxBus;
    @Inject protected RileyLinkUtil rileyLinkUtil;
    @Inject protected HasAndroidInjector injector;
    @Inject protected ResourceHelper resourceHelper;
    @Inject protected RileyLinkServiceData rileyLinkServiceData;
    @Inject protected ActivePluginProvider activePlugin;
    @Inject protected RileyLinkBLE rileyLinkBLE; // android-bluetooth management
    @Inject protected RFSpy rfspy; // interface for RL xxx Mhz radio.

    protected BluetoothAdapter bluetoothAdapter;
    protected RileyLinkBroadcastReceiver mBroadcastReceiver;
    protected RileyLinkBluetoothStateReceiver bluetoothStateReceiver;


    @Override
    public void onCreate() {
        super.onCreate();

        rileyLinkUtil.setEncoding(getEncoding());
        initRileyLinkServiceData();

        mBroadcastReceiver = new RileyLinkBroadcastReceiver(this);
        mBroadcastReceiver.registerBroadcasts(this);


        bluetoothStateReceiver = new RileyLinkBluetoothStateReceiver();
        bluetoothStateReceiver.registerBroadcasts(this);
    }

    /**
     * Get Encoding for RileyLink communication
     */
    public abstract RileyLinkEncodingType getEncoding();


    /**
     * If you have customized RileyLinkServiceData you need to override this
     */
    public abstract void initRileyLinkServiceData();


    @Override
    public boolean onUnbind(Intent intent) {
        //aapsLogger.warn(LTag.PUMPBTCOMM, "onUnbind");
        return super.onUnbind(intent);
    }


    @Override
    public void onRebind(Intent intent) {
        //aapsLogger.warn(LTag.PUMPBTCOMM, "onRebind");
        super.onRebind(intent);
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        //LOG.error("I die! I die!");

        // xyz rfspy.stopReader();
        rileyLinkBLE.disconnect(); // dispose of Gatt (disconnect and close)

        if (mBroadcastReceiver != null) {
            mBroadcastReceiver.unregisterBroadcasts(this);
        }

        if (bluetoothStateReceiver != null) {
            bluetoothStateReceiver.unregisterBroadcasts(this);
        }

    }


    public abstract RileyLinkCommunicationManager getDeviceCommunicationManager();

    public RileyLinkServiceState getRileyLinkServiceState() {
        return rileyLinkServiceData == null ? null : rileyLinkServiceData.rileyLinkServiceState;
    }

    // Here is where the wake-lock begins:
    // We've received a service startCommand, we grab the lock.
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return (START_STICKY);
    }


    public boolean bluetoothInit() {
        aapsLogger.debug(LTag.PUMPBTCOMM, "bluetoothInit: attempting to get an adapter");
        rileyLinkServiceData.setRileyLinkServiceState(RileyLinkServiceState.BluetoothInitializing);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (bluetoothAdapter == null) {
            aapsLogger.error("Unable to obtain a BluetoothAdapter.");
            rileyLinkServiceData.setServiceState(RileyLinkServiceState.BluetoothError, RileyLinkError.NoBluetoothAdapter);
        } else {

            if (!bluetoothAdapter.isEnabled()) {
                aapsLogger.error("Bluetooth is not enabled.");
                rileyLinkServiceData.setServiceState(RileyLinkServiceState.BluetoothError, RileyLinkError.BluetoothDisabled);
            } else {
                rileyLinkServiceData.setRileyLinkServiceState(RileyLinkServiceState.BluetoothReady);
                return true;
            }
        }

        return false;
    }


    // returns true if our Rileylink configuration changed
    public boolean reconfigureRileyLink(String deviceAddress) {

        rileyLinkServiceData.setRileyLinkServiceState(RileyLinkServiceState.RileyLinkInitializing);

        if (rileyLinkBLE.isConnected()) {
            if (deviceAddress.equals(rileyLinkServiceData.rileyLinkAddress)) {
                aapsLogger.info(LTag.PUMPBTCOMM, "No change to RL address.  Not reconnecting.");
                return false;
            } else {
                aapsLogger.warn(LTag.PUMPBTCOMM, "Disconnecting from old RL (" + rileyLinkServiceData.rileyLinkAddress
                        + "), reconnecting to new: " + deviceAddress);

                rileyLinkBLE.disconnect();
                // prolly need to shut down listening thread too?
                // SP.putString(MedtronicConst.Prefs.RileyLinkAddress, deviceAddress);

                rileyLinkServiceData.rileyLinkAddress = deviceAddress;
                rileyLinkBLE.findRileyLink(rileyLinkServiceData.rileyLinkAddress);
                return true;
            }
        } else {
            aapsLogger.debug(LTag.PUMPBTCOMM, "Using RL " + deviceAddress);

            if (rileyLinkServiceData.getRileyLinkServiceState() == RileyLinkServiceState.NotStarted) {
                if (!bluetoothInit()) {
                    aapsLogger.error("RileyLink can't get activated, Bluetooth is not functioning correctly. {}",
                            getError() != null ? getError().name() : "Unknown error (null)");
                    return false;
                }
            }

            rileyLinkBLE.findRileyLink(deviceAddress);

            return true;
        }
    }

    // FIXME: This needs to be run in a session so that is interruptable, has a separate thread, etc.
    public void doTuneUpDevice() {

        rileyLinkServiceData.setRileyLinkServiceState(RileyLinkServiceState.TuneUpDevice);
        setPumpDeviceState(PumpDeviceState.Sleeping);

        double lastGoodFrequency = 0.0d;

        if (rileyLinkServiceData.lastGoodFrequency == null) {
            lastGoodFrequency = sp.getDouble(RileyLinkConst.Prefs.LastGoodDeviceFrequency, 0.0d);
        } else {
            lastGoodFrequency = rileyLinkServiceData.lastGoodFrequency;
        }

        double newFrequency;

        newFrequency = getDeviceCommunicationManager().tuneForDevice();

        if ((newFrequency != 0.0) && (newFrequency != lastGoodFrequency)) {
            aapsLogger.info(LTag.PUMPBTCOMM, "Saving new pump frequency of {} MHz", newFrequency);
            sp.putDouble(RileyLinkConst.Prefs.LastGoodDeviceFrequency, newFrequency);
            rileyLinkServiceData.lastGoodFrequency = newFrequency;
            rileyLinkServiceData.tuneUpDone = true;
            rileyLinkServiceData.lastTuneUpTime = System.currentTimeMillis();
        }

        if (newFrequency == 0.0d) {
            // error tuning pump, pump not present ??
            rileyLinkServiceData.setServiceState(RileyLinkServiceState.PumpConnectorError, RileyLinkError.TuneUpOfDeviceFailed);
        } else {
            getDeviceCommunicationManager().clearNotConnectedCount();
            rileyLinkServiceData.setRileyLinkServiceState(RileyLinkServiceState.PumpConnectorReady);
        }
    }


    public abstract void setPumpDeviceState(PumpDeviceState pumpDeviceState);


    public void disconnectRileyLink() {

        if (rileyLinkBLE.isConnected()) {
            rileyLinkBLE.disconnect();
            rileyLinkServiceData.rileyLinkAddress = null;
            rileyLinkServiceData.rileyLinkName = null;
        }

        rileyLinkServiceData.setRileyLinkServiceState(RileyLinkServiceState.BluetoothReady);
    }

    @NotNull public RileyLinkBLE getRileyLinkBLE() {
        return rileyLinkBLE;
    }

    /**
     * Get Target Device for Service
     */
    public RileyLinkTargetDevice getRileyLinkTargetDevice() {
        return this.rileyLinkServiceData.targetDevice;
    }


    public void changeRileyLinkEncoding(RileyLinkEncodingType encodingType) {
        if (rfspy != null) {
            rfspy.setRileyLinkEncoding(encodingType);
        }
    }

    public RileyLinkError getError() {
        if (rileyLinkServiceData != null)
            return rileyLinkServiceData.rileyLinkError;
        else
            return null;
    }

    public boolean verifyConfiguration() {
        return verifyConfiguration(false);
    }

    public abstract boolean verifyConfiguration(boolean forceRileyLinkAddressRenewal);
}
