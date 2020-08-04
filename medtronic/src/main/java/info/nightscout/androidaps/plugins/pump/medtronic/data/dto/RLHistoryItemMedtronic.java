package info.nightscout.androidaps.plugins.pump.medtronic.data.dto;

import org.joda.time.LocalDateTime;

import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.data.RLHistoryItem;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkError;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkServiceState;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkTargetDevice;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedtronicCommandType;
import info.nightscout.androidaps.utils.resources.ResourceHelper;

public class RLHistoryItemMedtronic extends RLHistoryItem {

    private MedtronicCommandType medtronicCommandType;

    public RLHistoryItemMedtronic(MedtronicCommandType medtronicCommandType) {
        super(new LocalDateTime(), RLHistoryItemSource.MedtronicCommand, RileyLinkTargetDevice.MedtronicPump);
        this.medtronicCommandType = medtronicCommandType;
    }

    public String getDescription(ResourceHelper resourceHelper) {

        switch (this.source) {
            case RileyLink:
                return "State: " + resourceHelper.gs(serviceState.getResourceId(targetDevice))
                        + (this.errorCode == null ? "" : ", Error Code: " + errorCode);

            case MedtronicPump:
                return resourceHelper.gs(pumpDeviceState.getResourceId());

            case MedtronicCommand:
                return medtronicCommandType.name();

            default:
                return "Unknown Description";
        }
    }



}
