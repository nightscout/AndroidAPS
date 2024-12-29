package app.aaps.pump.omnipod.eros.data;

import androidx.annotation.NonNull;

import org.joda.time.LocalDateTime;

import javax.inject.Inject;

import app.aaps.core.interfaces.resources.ResourceHelper;
import app.aaps.pump.common.hw.rileylink.data.RLHistoryItem;
import app.aaps.pump.common.hw.rileylink.defs.RileyLinkTargetDevice;
import app.aaps.pump.omnipod.common.definition.OmnipodCommandType;
import dagger.android.HasAndroidInjector;

public class RLHistoryItemOmnipod extends RLHistoryItem {

    @Inject ResourceHelper rh;
    private final OmnipodCommandType omnipodCommandType;

    public RLHistoryItemOmnipod(@NonNull HasAndroidInjector injector, OmnipodCommandType omnipodCommandType) {
        super(new LocalDateTime(), RLHistoryItemSource.OmnipodCommand, RileyLinkTargetDevice.Omnipod);
        injector.androidInjector().inject(this);
        this.omnipodCommandType = omnipodCommandType;
    }

    @Override
    public String getDescription(@NonNull ResourceHelper rh) {
        if (RLHistoryItemSource.OmnipodCommand.equals(source)) {
            return rh.gs(omnipodCommandType.getResourceId());
        }
        return super.getDescription(rh);
    }

}
