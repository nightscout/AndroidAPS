package info.nightscout.androidaps.plugins.pump.omnipod.eros.data;

import org.joda.time.LocalDateTime;

import javax.inject.Inject;

import app.aaps.core.interfaces.resources.ResourceHelper;
import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.data.RLHistoryItem;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkTargetDevice;
import info.nightscout.androidaps.plugins.pump.omnipod.common.definition.OmnipodCommandType;

public class RLHistoryItemOmnipod extends RLHistoryItem {

    @Inject ResourceHelper rh;
    private final OmnipodCommandType omnipodCommandType;

    public RLHistoryItemOmnipod(HasAndroidInjector injector, OmnipodCommandType omnipodCommandType) {
        super(new LocalDateTime(), RLHistoryItemSource.OmnipodCommand, RileyLinkTargetDevice.Omnipod);
        injector.androidInjector().inject(this);
        this.omnipodCommandType = omnipodCommandType;
    }

    @Override
    public String getDescription(ResourceHelper rh) {
        if (RLHistoryItemSource.OmnipodCommand.equals(source)) {
            return rh.gs(omnipodCommandType.getResourceId());
        }
        return super.getDescription(rh);
    }

}
