package info.nightscout.androidaps.plugins.pump.medtronic.comm.ui;

import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkConst;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkUtil;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.MedtronicCommunicationManager;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedtronicCommandType;
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedtronicUtil;
import info.nightscout.androidaps.utils.resources.ResourceHelper;

/**
 * Created by andy on 6/14/18.
 */
public class MedtronicUIComm {

    private final AAPSLogger aapsLogger;

    MedtronicCommunicationManager mcmInstance = null;
    MedtronicUIPostprocessor uiPostprocessor;

    public MedtronicUIComm(
            AAPSLogger aapsLogger,
            RxBusWrapper rxBus,
            ResourceHelper resourceHelper
    ) {
        this.aapsLogger = aapsLogger;

        uiPostprocessor = new MedtronicUIPostprocessor(aapsLogger, rxBus, resourceHelper);
    }


    private MedtronicCommunicationManager getCommunicationManager() {
        if (mcmInstance == null) {
            mcmInstance = MedtronicCommunicationManager.getInstance();
        }

        return mcmInstance;
    }


    public synchronized MedtronicUITask executeCommand(MedtronicCommandType commandType, Object... parameters) {

        aapsLogger.warn(LTag.PUMP, "Execute Command: " + commandType.name());

        MedtronicUITask task = new MedtronicUITask(commandType, parameters);

        MedtronicUtil.setCurrentCommand(commandType);

        // new Thread(() -> {
        // LOG.warn("@@@ Start Thread");
        //
        // task.execute(getCommunicationManager());
        //
        // LOG.warn("@@@ End Thread");
        // });

        task.execute(getCommunicationManager());

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

        task.postProcess(uiPostprocessor);

        return task;

    }


    /**
     * We return 25s as waitTime (17 for wakeUp, and addtional 8 for data retrieval) for normal commands and
     * 120s for History. Real time for returning data would be arround 5s, but lets be sure.
     *
     * @param commandType
     * @return
     */
    private int getMaxWaitTime(MedtronicCommandType commandType) {
        if (commandType == MedtronicCommandType.GetHistoryData)
            return 120;
        else
            return 25;
    }


    public int getInvalidResponsesCount() {
        return getCommunicationManager().getNotConnectedCount();
    }


    public void startTunning() {
        RileyLinkUtil.sendBroadcastMessage(RileyLinkConst.IPC.MSG_PUMP_tunePump);
    }
}
