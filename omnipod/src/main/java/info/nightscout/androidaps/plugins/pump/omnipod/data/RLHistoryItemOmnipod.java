package info.nightscout.androidaps.plugins.pump.omnipod.data;

import org.joda.time.LocalDateTime;

import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.data.RLHistoryItem;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkTargetDevice;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.OmnipodCommandType;
import info.nightscout.androidaps.utils.resources.ResourceHelper;

public class RLHistoryItemOmnipod extends RLHistoryItem {

    private OmnipodCommandType omnipodCommandType;

    public RLHistoryItemOmnipod(OmnipodCommandType omnipodCommandType) {
        super(new LocalDateTime(), RLHistoryItemSource.OmnipodCommand, RileyLinkTargetDevice.Omnipod);
        this.omnipodCommandType = omnipodCommandType;
    }

    @Override
    public String getDescription(ResourceHelper resourceHelper) {
        if (RLHistoryItemSource.OmnipodCommand.equals(source)) {
            return omnipodCommandType.name();
        }
        return super.getDescription(resourceHelper);
    }

}
