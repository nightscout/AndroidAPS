package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.data;

import org.joda.time.LocalDateTime;

import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkError;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkServiceState;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkTargetDevice;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedtronicCommandType;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.PumpDeviceState;
import info.nightscout.androidaps.utils.resources.ResourceHelper;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.OmnipodCommandType;


/**
 * Created by andy on 5/19/18.
 */

public class RLHistoryItem {

    private MedtronicCommandType medtronicCommandType;
    private LocalDateTime dateTime;
    private RLHistoryItemSource source;
    private RileyLinkServiceState serviceState;
    private RileyLinkError errorCode;

    private RileyLinkTargetDevice targetDevice;
    private PumpDeviceState pumpDeviceState;
    private OmnipodCommandType omnipodCommandType;


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


    public RLHistoryItem(MedtronicCommandType medtronicCommandType) {
        this.dateTime = new LocalDateTime();
        this.medtronicCommandType = medtronicCommandType;
        source = RLHistoryItemSource.MedtronicCommand;
    }


    public RLHistoryItem(OmnipodCommandType omnipodCommandType) {
        this.dateTime = new LocalDateTime();
        this.omnipodCommandType = omnipodCommandType;
        source = RLHistoryItemSource.OmnipodCommand;
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


    public String getDescription(ResourceHelper resourceHelper) {

        // TODO extend when we have Omnipod
        switch (this.source) {
            case RileyLink:
                return "State: " + resourceHelper.gs(serviceState.getResourceId(targetDevice))
                        + (this.errorCode == null ? "" : ", Error Code: " + errorCode);

            case MedtronicPump:
                return resourceHelper.gs(pumpDeviceState.getResourceId());

            case MedtronicCommand:
                return medtronicCommandType.name();

            case OmnipodCommand:
                return omnipodCommandType.name();

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

        private String desc;


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
            return o2.dateTime.compareTo(o1.dateTime);
        }
    }

}
