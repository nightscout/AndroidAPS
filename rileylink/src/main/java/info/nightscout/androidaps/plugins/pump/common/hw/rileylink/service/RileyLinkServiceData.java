package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service;

import javax.inject.Inject;
import javax.inject.Singleton;

import info.nightscout.androidaps.interfaces.ActivePluginProvider;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.plugins.pump.common.events.EventRileyLinkDeviceStatusChange;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkUtil;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RileyLinkFirmwareVersion;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RileyLinkTargetFrequency;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.data.RLHistoryItem;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkError;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkServiceState;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkTargetDevice;

/**
 * Created by andy on 16/05/2018.
 */

// FIXME encapsulation
@Singleton
public class RileyLinkServiceData {

    @Inject AAPSLogger aapsLogger;
    @Inject RileyLinkUtil rileyLinkUtil;
    @Inject RxBusWrapper rxBus;
    @Inject ActivePluginProvider activePlugin;

    boolean tuneUpDone = false;
    public RileyLinkError rileyLinkError;
    public RileyLinkServiceState rileyLinkServiceState = RileyLinkServiceState.NotStarted;
    private long lastServiceStateChange = 0L;
    public RileyLinkFirmwareVersion firmwareVersion; // here we have "compatibility level" version
    public RileyLinkTargetFrequency rileyLinkTargetFrequency;
    public String rileyLinkAddress;
    public String rileyLinkName;
    public Integer batteryLevel;
    long lastTuneUpTime = 0L;
    public Double lastGoodFrequency;

    // bt version
    public String versionBLE113;
    // radio version
    public String versionCC110;

    public RileyLinkTargetDevice targetDevice;

    // Medtronic Pump
    public String pumpID;
    public byte[] pumpIDBytes;

    @Inject
    public RileyLinkServiceData() {
    }

    public void setPumpID(String pumpId, byte[] pumpIdBytes) {
        this.pumpID = pumpId;
        this.pumpIDBytes = pumpIdBytes;
    }

    public void setRileyLinkServiceState(RileyLinkServiceState newState) {
        setServiceState(newState, null);
    }

    public RileyLinkServiceState getRileyLinkServiceState() {
        return workWithServiceState(null, null, false);
    }

    public void setServiceState(RileyLinkServiceState newState, RileyLinkError errorCode) {
        workWithServiceState(newState, errorCode, true);
    }

    public long getLastServiceStateChange() {
        return lastServiceStateChange;
    }

    private synchronized RileyLinkServiceState workWithServiceState(RileyLinkServiceState newState, RileyLinkError errorCode, boolean set) {
        if (set) {
            rileyLinkServiceState = newState;
            lastServiceStateChange = System.currentTimeMillis();
            this.rileyLinkError = errorCode;

            aapsLogger.info(LTag.PUMP, "RileyLink State Changed: {} {}", newState, errorCode == null ? "" : " - Error State: " + errorCode.name());

            rileyLinkUtil.getRileyLinkHistory().add(new RLHistoryItem(rileyLinkServiceState, errorCode, targetDevice));
            rxBus.send(new EventRileyLinkDeviceStatusChange(targetDevice, newState, errorCode));
            return null;
        } else {
            return rileyLinkServiceState;
        }
    }

}
