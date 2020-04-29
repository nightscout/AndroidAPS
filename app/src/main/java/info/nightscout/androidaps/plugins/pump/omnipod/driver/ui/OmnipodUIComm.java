package info.nightscout.androidaps.plugins.pump.omnipod.driver.ui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.MedtronicCommunicationManager;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.ui.MedtronicUIPostprocessor;
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedtronicUtil;
import info.nightscout.androidaps.plugins.pump.omnipod.OmnipodPumpPlugin;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.OmnipodCommunicationManager;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.OmnipodCommunicationManagerInterface;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.OmnipodCommandType;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.OmnipodPumpPluginInterface;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.OmnipodPumpStatus;
import info.nightscout.androidaps.plugins.pump.omnipod.util.OmnipodUtil;

/**
 * Created by andy on 4.8.2019
 */
public class OmnipodUIComm {

    private static final Logger LOG = LoggerFactory.getLogger(L.PUMP);

    private final HasAndroidInjector injector;
    private final AAPSLogger aapsLogger;
    private final OmnipodUtil omnipodUtil;
    private final OmnipodCommunicationManagerInterface omnipodCommunicationManager;
    private final OmnipodUIPostprocessor omnipodUIPostprocessor;


    //OmnipodCommunicationManagerInterface ocmInstance = null;
    //OmnipodUIPostprocessor uiPostprocessor; // = new OmnipodUIPostprocessor();


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


    private OmnipodCommunicationManagerInterface getCommunicationManager() {
        return omnipodCommunicationManager;
    }

//    public OmnipodUIComm(OmnipodCommunicationManagerInterface communicationManager, OmnipodPumpPluginInterface plugin, OmnipodPumpStatus status) {
//        ocmInstance = communicationManager;
//        uiPostprocessor = new OmnipodUIPostprocessor(plugin, status);
//    }




    public OmnipodUITask executeCommand(OmnipodCommandType commandType, Object... parameters) {

        if (isLogEnabled())
            LOG.warn("Execute Command: " + commandType.name());

        OmnipodUITask task = new OmnipodUITask(injector, commandType, parameters);

        omnipodUtil.setCurrentCommand(commandType);

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

        task.postProcess(omnipodUIPostprocessor);

        return task;

    }


    private boolean isLogEnabled() {
        return L.isEnabled(L.PUMP);
    }

}
