package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service;

import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RileyLinkFirmwareVersion;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkError;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkServiceState;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkTargetDevice;

/**
 * Created by andy on 16/05/2018.
 */

public class RileyLinkServiceData {

    public boolean tuneUpDone = false;
    public RileyLinkError errorCode;
    public RileyLinkServiceState serviceState = RileyLinkServiceState.NotStarted;
    public String rileylinkAddress;
    public long lastTuneUpTime = 0L;
    public Double lastGoodFrequency;

    // bt version
    public String versionBLE113;
    // radio version
    public RileyLinkFirmwareVersion versionCC110;

    public RileyLinkTargetDevice targetDevice;

    // Medtronic Pump
    public String pumpID;
    public byte[] pumpIDBytes;


    public RileyLinkServiceData(RileyLinkTargetDevice targetDevice) {
        this.targetDevice = targetDevice;
    }


    public void setPumpID(String pumpId, byte[] pumpIdBytes) {
        this.pumpID = pumpId;
        this.pumpIDBytes = pumpIdBytes;
    }

}
