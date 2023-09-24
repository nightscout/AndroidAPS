package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.data;

import org.joda.time.LocalDateTime;

import app.aaps.core.interfaces.resources.ResourceHelper;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkError;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkServiceState;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkTargetDevice;
import info.nightscout.pump.common.defs.PumpDeviceState;


/**
 * Created by andy on 5/19/18.
 */

public class RLHistoryItem {

    protected LocalDateTime dateTime;
    protected RLHistoryItemSource source;
    protected RileyLinkServiceState serviceState;
    protected RileyLinkError errorCode;

    protected RileyLinkTargetDevice targetDevice;
    protected PumpDeviceState pumpDeviceState;

    public RLHistoryItem(LocalDateTime dateTime, RLHistoryItemSource source, RileyLinkTargetDevice targetDevice) {
        this.dateTime = dateTime;
        this.source = source;
        this.targetDevice = targetDevice;
    }

    public RLHistoryItem(RileyLinkServiceState serviceState, RileyLinkError errorCode,
                         RileyLinkTargetDevice targetDevice) {
        this.targetDevice = targetDevice;
        this.dateTime = new LocalDateTime();
        this.serviceState = serviceState;
        this.errorCode = errorCode;
        this.source = RLHistoryItemSource.RileyLink;
    }

    public RLHistoryItem(PumpDeviceState pumpDeviceState, RileyLinkTargetDevice targetDevice) {
        this.pumpDeviceState = pumpDeviceState;
        this.dateTime = new LocalDateTime();
        this.targetDevice = targetDevice;
        this.source = RLHistoryItemSource.MedtronicPump;
    }

    public LocalDateTime getDateTime() {
        return dateTime;
    }

    public RileyLinkServiceState getServiceState() {
        return serviceState;
    }

    public RileyLinkError getErrorCode() {
        return errorCode;
    }

    public String getDescription(ResourceHelper rh) {
        switch (this.source) {
            case RileyLink:
                return "State: " + rh.gs(serviceState.getResourceId())
                        + (this.errorCode == null ? "" : ", Error Code: " + errorCode);
            case MedtronicPump:
                return rh.gs(pumpDeviceState.getResourceId());
            default:
                return "Unknown Description";
        }
    }

    public RLHistoryItemSource getSource() {
        return source;
    }

    public PumpDeviceState getPumpDeviceState() {
        return pumpDeviceState;
    }

    public enum RLHistoryItemSource {
        RileyLink("RileyLink"), //
        MedtronicPump("Medtronic"), //
        MedtronicCommand("Medtronic"), //
        OmnipodCommand("Omnipod");

        private final String desc;


        RLHistoryItemSource(String desc) {
            this.desc = desc;
        }

        public String getDesc() {
            return desc;
        }
    }

    public static class Comparator implements java.util.Comparator<RLHistoryItem> {
        @Override
        public int compare(RLHistoryItem o1, RLHistoryItem o2) {
            return o2.dateTime.compareTo(o1.getDateTime());
        }
    }

}
