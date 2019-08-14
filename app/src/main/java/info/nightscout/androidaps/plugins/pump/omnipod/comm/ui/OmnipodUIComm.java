package info.nightscout.androidaps.plugins.pump.omnipod.comm.ui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.OmnipodCommunicationManager;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.OmnipodCommunicationManagerInterface;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.OmnipodCommandType;
import info.nightscout.androidaps.plugins.pump.omnipod.util.OmnipodUtil;

/**
 * Created by andy on 4.8.2019
 */
public class OmnipodUIComm {

    private static final Logger LOG = LoggerFactory.getLogger(L.PUMP);

    OmnipodCommunicationManagerInterface ocmInstance = null;
    OmnipodUIPostprocessor uiPostprocessor = new OmnipodUIPostprocessor();


    private OmnipodCommunicationManagerInterface getCommunicationManager() {
        return ocmInstance;
    }

    public OmnipodUIComm(OmnipodCommunicationManagerInterface communicationManager) {
        ocmInstance = communicationManager;
    }


    public synchronized OmnipodUITask executeCommand(OmnipodCommandType commandType, Object... parameters) {

        if (isLogEnabled())
            LOG.warn("Execute Command: " + commandType.name());

        OmnipodUITask task = new OmnipodUITask(commandType, parameters);

        OmnipodUtil.setCurrentCommand(commandType);

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


    private boolean isLogEnabled() {
        return L.isEnabled(L.PUMP);
    }

}
