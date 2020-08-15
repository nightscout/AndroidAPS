package info.nightscout.androidaps.plugins.pump.omnipod.data;

import org.joda.time.LocalDateTime;

import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.data.RLHistoryItem;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkTargetDevice;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedtronicCommandType;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.OmnipodCommandType;
import info.nightscout.androidaps.utils.resources.ResourceHelper;

public class RLHistoryItemOmnipod extends RLHistoryItem {

    private OmnipodCommandType omnipodCommandType;

    public RLHistoryItemOmnipod(OmnipodCommandType omnipodCommandType) {
        super(new LocalDateTime(), RLHistoryItemSource.OmnipodCommand, RileyLinkTargetDevice.Omnipod);
        this.omnipodCommandType = omnipodCommandType;
    }

    public String getDescription(ResourceHelper resourceHelper) {

        switch (this.source) {
            case RileyLink:
                return "State: " + resourceHelper.gs(serviceState.getResourceId(targetDevice))
                        + (this.errorCode == null ? "" : ", Error Code: " + errorCode);

            case MedtronicPump:
                return resourceHelper.gs(pumpDeviceState.getResourceId());

            case OmnipodCommand:
                return omnipodCommandType.name();

            default:
                return "Unknown Description";
        }
    }



}
