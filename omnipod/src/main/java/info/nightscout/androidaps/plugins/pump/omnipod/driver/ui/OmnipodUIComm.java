package info.nightscout.androidaps.plugins.pump.omnipod.driver.ui;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.events.EventRefreshOverview;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkUtil;
import info.nightscout.androidaps.plugins.pump.omnipod.data.RLHistoryItemOmnipod;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.IOmnipodManager;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.OmnipodCommandType;

/**
 * Created by andy on 4.8.2019
 */
public class OmnipodUIComm {

    private final HasAndroidInjector injector;
    private final AAPSLogger aapsLogger;
    private final IOmnipodManager omnipodManager;
    private final RileyLinkUtil rileyLinkUtil;
    private final RxBusWrapper rxBus;

    public OmnipodUIComm(
            HasAndroidInjector injector,
            AAPSLogger aapsLogger,
            IOmnipodManager omnipodManager,
            RileyLinkUtil rileyLinkUtil,
            RxBusWrapper rxBus
    ) {
        this.injector = injector;
        this.aapsLogger = aapsLogger;
        this.omnipodManager = omnipodManager;
        this.rileyLinkUtil = rileyLinkUtil;
        this.rxBus = rxBus;
    }

    public OmnipodUITask executeCommand(OmnipodCommandType commandType, Object... parameters) {
        aapsLogger.warn(LTag.PUMP, "Execute Command: " + commandType.name());

        OmnipodUITask task = new OmnipodUITask(injector, commandType, parameters);

        rileyLinkUtil.getRileyLinkHistory().add(new RLHistoryItemOmnipod(commandType));

        task.execute(this.omnipodManager);

        if (!task.isReceived()) {
            aapsLogger.warn(LTag.PUMP, "Reply not received for " + commandType);
        }

        rxBus.send(new EventRefreshOverview("Omnipod command: "+ commandType.name(), true));

        return task;
    }

}
