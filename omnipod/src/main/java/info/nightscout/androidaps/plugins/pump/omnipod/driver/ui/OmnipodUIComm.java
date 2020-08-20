package info.nightscout.androidaps.plugins.pump.omnipod.driver.ui;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
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
    private final OmnipodUIPostprocessor omnipodUIPostprocessor;
    private final IOmnipodManager omnipodCommunicationManager;
    private RileyLinkUtil rileyLinkUtil;

    public OmnipodUIComm(
            HasAndroidInjector injector,
            AAPSLogger aapsLogger,
            OmnipodUIPostprocessor omnipodUIPostprocessor,
            IOmnipodManager omnipodCommunicationManager,
            RileyLinkUtil rileyLinkUtil
    ) {
        this.injector = injector;
        this.aapsLogger = aapsLogger;
        this.omnipodUIPostprocessor = omnipodUIPostprocessor;
        this.omnipodCommunicationManager = omnipodCommunicationManager;
        this.rileyLinkUtil = rileyLinkUtil;
    }


    public OmnipodUITask executeCommand(OmnipodCommandType commandType, Object... parameters) {

        aapsLogger.warn(LTag.PUMP, "Execute Command: " + commandType.name());

        OmnipodUITask task = new OmnipodUITask(injector, commandType, parameters);

        rileyLinkUtil.getRileyLinkHistory().add(new RLHistoryItemOmnipod(commandType));

        task.execute(this.omnipodCommunicationManager);

        if (!task.isReceived()) {
            aapsLogger.warn(LTag.PUMP, "Reply not received for " + commandType);
        }

        task.postProcess(omnipodUIPostprocessor);

        return task;

    }

}
