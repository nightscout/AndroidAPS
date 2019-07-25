package info.nightscout.androidaps.plugins.pump.medtronic.comm.ui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkConst;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkUtil;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.MedtronicCommunicationManager;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedtronicCommandType;
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedtronicUtil;

/**
 * Created by andy on 6/14/18.
 */
public class MedtronicUIComm {

    private static final Logger LOG = LoggerFactory.getLogger(L.PUMP);

    MedtronicCommunicationManager mcmInstance = null;
    MedtronicUIPostprocessor uiPostprocessor = new MedtronicUIPostprocessor();


    private MedtronicCommunicationManager getCommunicationManager() {
        if (mcmInstance == null) {
            mcmInstance = MedtronicCommunicationManager.getInstance();
        }

        return mcmInstance;
    }


    public synchronized MedtronicUITask executeCommand(MedtronicCommandType commandType, Object... parameters) {

        if (isLogEnabled())
            LOG.warn("Execute Command: " + commandType.name());

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

        if (!task.isReceived() && isLogEnabled()) {
            LOG.warn("Reply not received for " + commandType);
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

    private boolean isLogEnabled() {
        return L.isEnabled(L.PUMP);
    }

}
