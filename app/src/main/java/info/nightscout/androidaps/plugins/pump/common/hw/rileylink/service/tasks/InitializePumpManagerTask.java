package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks;

import org.slf4j.Logger;

import javax.inject.Inject;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.interfaces.ActivePluginProvider;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.logging.StacktraceLoggerWrapper;
import info.nightscout.androidaps.plugins.pump.common.PumpPluginAbstract;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkCommunicationManager;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkConst;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkUtil;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkError;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkServiceState;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkTargetDevice;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.data.ServiceTransport;
import info.nightscout.androidaps.utils.SP;

/**
 * Created by geoff on 7/9/16.
 * <p>
 * This class is intended to be run by the Service, for the Service. Not intended for clients to run.
 */
public class InitializePumpManagerTask extends ServiceTask {

    @Inject ActivePluginProvider activePlugin;

    private static final String TAG = "InitPumpManagerTask";
    private RileyLinkTargetDevice targetDevice;
    private static final Logger LOG = StacktraceLoggerWrapper.getLogger(L.PUMPCOMM);

    public InitializePumpManagerTask(HasAndroidInjector injector, RileyLinkTargetDevice targetDevice) {
        super(injector);
        this.targetDevice = targetDevice;
    }


    public InitializePumpManagerTask(HasAndroidInjector injector, ServiceTransport transport) {
        super(injector, transport);
    }


    @Override
    public void run() {

        double lastGoodFrequency = 0.0d;

        if (RileyLinkUtil.getInstance().getRileyLinkServiceData().lastGoodFrequency == null) {

            lastGoodFrequency = SP.getDouble(RileyLinkConst.Prefs.LastGoodDeviceFrequency, 0.0d);
            lastGoodFrequency = Math.round(lastGoodFrequency * 1000d) / 1000d;

            RileyLinkUtil.getInstance().getRileyLinkServiceData().lastGoodFrequency = lastGoodFrequency;

//            if (RileyLinkUtil.getRileyLinkTargetFrequency() == null) {
//                String pumpFrequency = SP.getString(MedtronicConst.Prefs.PumpFrequency, null);
//            }
        } else {
            lastGoodFrequency = RileyLinkUtil.getInstance().getRileyLinkServiceData().lastGoodFrequency;
        }

        RileyLinkCommunicationManager rileyLinkCommunicationManager = ((PumpPluginAbstract)activePlugin.getActivePump()).getRileyLinkService().getDeviceCommunicationManager();

        if ((lastGoodFrequency > 0.0d)
                && rileyLinkCommunicationManager.isValidFrequency(lastGoodFrequency)) {

            RileyLinkUtil.getInstance().setServiceState(RileyLinkServiceState.RileyLinkReady);

            if (L.isEnabled(L.PUMPCOMM))
                LOG.info("Setting radio frequency to {} MHz", lastGoodFrequency);

            rileyLinkCommunicationManager.setRadioFrequencyForPump(lastGoodFrequency);

            boolean foundThePump = rileyLinkCommunicationManager.tryToConnectToDevice();

            if (foundThePump) {
                RileyLinkUtil.getInstance().setServiceState(RileyLinkServiceState.PumpConnectorReady);
            } else {
                RileyLinkUtil.getInstance().setServiceState(RileyLinkServiceState.PumpConnectorError,
                        RileyLinkError.NoContactWithDevice);
                RileyLinkUtil.getInstance().sendBroadcastMessage(RileyLinkConst.IPC.MSG_PUMP_tunePump);
            }

        } else {
            RileyLinkUtil.getInstance().sendBroadcastMessage(RileyLinkConst.IPC.MSG_PUMP_tunePump);
        }
    }
}
