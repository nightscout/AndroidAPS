package info.nightscout.androidaps.plugins.pump.omnipod.driver.ui;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.OmnipodCommandType;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.OmnipodCommunicationManagerInterface;
import info.nightscout.androidaps.plugins.pump.omnipod.util.OmnipodUtil;

/**
 * Created by andy on 4.8.2019
 */
public class OmnipodUIComm {

    private final HasAndroidInjector injector;
    private final AAPSLogger aapsLogger;
    private final OmnipodUtil omnipodUtil;
    private final OmnipodCommunicationManagerInterface omnipodCommunicationManager;
    private final OmnipodUIPostprocessor omnipodUIPostprocessor;


    public OmnipodUIComm(
            HasAndroidInjector injector,
            AAPSLogger aapsLogger,
            OmnipodUtil omnipodUtil,
            OmnipodUIPostprocessor omnipodUIPostprocessor,
            OmnipodCommunicationManagerInterface omnipodCommunicationManager
    ) {
        this.injector = injector;
        this.aapsLogger = aapsLogger;
        this.omnipodUtil = omnipodUtil;
        this.omnipodUIPostprocessor = omnipodUIPostprocessor;
        this.omnipodCommunicationManager = omnipodCommunicationManager;
    }


    public OmnipodUITask executeCommand(OmnipodCommandType commandType, Object... parameters) {

        aapsLogger.warn(LTag.PUMP, "Execute Command: " + commandType.name());

        OmnipodUITask task = new OmnipodUITask(injector, commandType, parameters);

        omnipodUtil.setCurrentCommand(commandType);

        // new Thread(() -> {
        // LOG.warn("@@@ Start Thread");
        //
        // task.execute(getCommunicationManager());
        //
        // LOG.warn("@@@ End Thread");
        // });

        task.execute(this.omnipodCommunicationManager);

        // for (int i = 0; i < getMaxWaitTime(commandType); i++) {
        // synchronized (task) {
        // // try {
        // //
        // // //task.wait(1000);
        // // } catch (InterruptedException e) {
        // // LOG.error("executeCommand InterruptedException", e);
        // // }
        //
        //
        // SystemClock.sleep(1000);
        // }
        //
        // if (task.isReceived()) {
        // break;
        // }
        // }

        if (!task.isReceived()) {
            aapsLogger.warn(LTag.PUMP, "Reply not received for " + commandType);
        }

        task.postProcess(omnipodUIPostprocessor);

        return task;

    }

}
