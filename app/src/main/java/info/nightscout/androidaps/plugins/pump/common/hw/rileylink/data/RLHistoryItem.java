package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.data;

import org.joda.time.LocalDateTime;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkError;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkServiceState;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkTargetDevice;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedtronicCommandType;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.PumpDeviceState;

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


    public LocalDateTime getDateTime() {
        return dateTime;
    }


    public RileyLinkServiceState getServiceState() {
        return serviceState;
    }


    public RileyLinkError getErrorCode() {
        return errorCode;
    }


    public String getDescription() {

        // TODO extend when we have Omnipod
        switch (this.source) {
            case RileyLink:
                return "State: " + MainApp.gs(serviceState.getResourceId(targetDevice))
                    + (this.errorCode == null ? "" : ", Error Code: " + errorCode);

            case MedtronicPump:
                return MainApp.gs(pumpDeviceState.getResourceId());

            case MedtronicCommand:
                return medtronicCommandType.name();

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
        MedtronicCommand("Medtronic");

        private String desc;


        RLHistoryItemSource(String desc) {
            this.desc = desc;
        }


        public String getDesc() {
            return desc;
        }
    }

}
