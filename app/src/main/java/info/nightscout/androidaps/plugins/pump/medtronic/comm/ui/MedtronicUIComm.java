package info.nightscout.androidaps.plugins.pump.medtronic.comm.ui;

import javax.inject.Inject;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.MedtronicCommunicationManager;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedtronicCommandType;
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedtronicUtil;

/**
 * Created by andy on 6/14/18.
 */
public class MedtronicUIComm {

    private final HasAndroidInjector injector;
    private final AAPSLogger aapsLogger;
    private final MedtronicUtil medtronicUtil;
    private final MedtronicCommunicationManager medtronicCommunicationManager;
    private final MedtronicUIPostprocessor medtronicUIPostprocessor;

    @Inject
    public MedtronicUIComm(
            HasAndroidInjector injector,
            AAPSLogger aapsLogger,
            MedtronicUtil medtronicUtil,
            MedtronicUIPostprocessor medtronicUIPostprocessor,
            MedtronicCommunicationManager medtronicCommunicationManager
    ) {
        this.injector = injector;
        this.aapsLogger = aapsLogger;
        this.medtronicUtil = medtronicUtil;
        this.medtronicUIPostprocessor = medtronicUIPostprocessor;
        this.medtronicCommunicationManager = medtronicCommunicationManager;
    }

    public synchronized MedtronicUITask executeCommand(MedtronicCommandType commandType, Object... parameters) {

        aapsLogger.warn(LTag.PUMP, "Execute Command: " + commandType.name());

        MedtronicUITask task = new MedtronicUITask(injector, commandType, parameters);

        medtronicUtil.setCurrentCommand(commandType);

        // new Thread(() -> {
        // LOG.warn("@@@ Start Thread");
        //
        // task.execute(getCommunicationManager());
        //
        // LOG.warn("@@@ End Thread");
        // });

        task.execute(medtronicCommunicationManager);

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

        task.postProcess(medtronicUIPostprocessor);

        return task;

    }

    public int getInvalidResponsesCount() {
        return medtronicCommunicationManager.getNotConnectedCount();
    }
}
