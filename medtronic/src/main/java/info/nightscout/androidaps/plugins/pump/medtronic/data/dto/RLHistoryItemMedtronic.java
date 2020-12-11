package info.nightscout.androidaps.plugins.pump.medtronic.data.dto;

import org.joda.time.LocalDateTime;

import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.data.RLHistoryItem;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkTargetDevice;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedtronicCommandType;
import info.nightscout.androidaps.utils.resources.ResourceHelper;

public class RLHistoryItemMedtronic extends RLHistoryItem {

    private final MedtronicCommandType medtronicCommandType;

    public RLHistoryItemMedtronic(MedtronicCommandType medtronicCommandType) {
        super(new LocalDateTime(), RLHistoryItemSource.MedtronicCommand, RileyLinkTargetDevice.MedtronicPump);
        this.medtronicCommandType = medtronicCommandType;
    }

    @Override
    public String getDescription(ResourceHelper resourceHelper) {
        if (RLHistoryItemSource.MedtronicCommand.equals(source)) {
            return medtronicCommandType.name();
        }

        return super.getDescription(resourceHelper);
    }

}
