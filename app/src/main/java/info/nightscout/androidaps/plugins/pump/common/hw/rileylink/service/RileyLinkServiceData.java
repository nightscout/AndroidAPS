package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service;

import javax.inject.Inject;
import javax.inject.Singleton;

import info.nightscout.androidaps.interfaces.ActivePluginProvider;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.plugins.common.ManufacturerType;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkUtil;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RileyLinkFirmwareVersion;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RileyLinkTargetFrequency;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.data.RLHistoryItem;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkError;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkServiceState;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkTargetDevice;
import info.nightscout.androidaps.plugins.pump.medtronic.events.EventMedtronicDeviceStatusChange;
import info.nightscout.androidaps.plugins.pump.omnipod.events.EventOmnipodDeviceStatusChange;

/**
 * Created by andy on 16/05/2018.
 */

@Singleton
public class RileyLinkServiceData {

    @Inject AAPSLogger aapsLogger;
    @Inject RileyLinkUtil rileyLinkUtil;
    @Inject RxBusWrapper rxBus;
    @Inject ActivePluginProvider activePlugin;

    boolean tuneUpDone = false;
    public RileyLinkError rileyLinkError;
    public RileyLinkServiceState rileyLinkServiceState = RileyLinkServiceState.NotStarted;
    public RileyLinkFirmwareVersion firmwareVersion;
    public RileyLinkTargetFrequency rileyLinkTargetFrequency; // TODO this might not be correct place

    public String rileylinkAddress;
    long lastTuneUpTime = 0L;
    public Double lastGoodFrequency;

    // bt version
    public String versionBLE113;
    // radio version
    public RileyLinkFirmwareVersion versionCC110;

    public RileyLinkTargetDevice targetDevice; // TODO this might not be correct place

    // Medtronic Pump
    public String pumpID;
    public byte[] pumpIDBytes;

    @Inject
    public RileyLinkServiceData() {}

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

    private synchronized RileyLinkServiceState workWithServiceState(RileyLinkServiceState newState, RileyLinkError errorCode, boolean set) {

        if (set) {

            rileyLinkServiceState = newState;
            this.rileyLinkError = errorCode;

            aapsLogger.info(LTag.PUMP, "RileyLink State Changed: {} {}", newState, errorCode == null ? "" : " - Error State: " + errorCode.name());

            rileyLinkUtil.getRileyLinkHistory().add(new RLHistoryItem(rileyLinkServiceState, errorCode, targetDevice));
            if (activePlugin.getActivePump().manufacturer()== ManufacturerType.Medtronic)
                rxBus.send(new EventMedtronicDeviceStatusChange(newState, errorCode));
            else {
                rxBus.send(new EventOmnipodDeviceStatusChange(newState, errorCode));
            }
            return null;

        } else {
            return rileyLinkServiceState;
        }

    }

}
