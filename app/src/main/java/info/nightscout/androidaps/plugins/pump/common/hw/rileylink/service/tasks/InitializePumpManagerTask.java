package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks;

import android.util.Log;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkConst;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkUtil;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkError;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkServiceState;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkTargetDevice;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.data.ServiceTransport;
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedtronicConst;
import info.nightscout.androidaps.utils.SP;

/**
 * Created by geoff on 7/9/16.
 * <p>
 * This class is intended to be run by the Service, for the Service. Not intended for clients to run.
 */
public class InitializePumpManagerTask extends ServiceTask {

    private static final String TAG = "InitPumpManagerTask";
    private RileyLinkTargetDevice targetDevice;
    private static final Logger LOG = LoggerFactory.getLogger(L.PUMPCOMM);

    public InitializePumpManagerTask(RileyLinkTargetDevice targetDevice) {
        super();
        this.targetDevice = targetDevice;
    }


    public InitializePumpManagerTask(ServiceTransport transport) {
        super(transport);
    }


    @Override
    public void run() {

        double lastGoodFrequency = 0.0d;

        if (RileyLinkUtil.getRileyLinkServiceData().lastGoodFrequency==null) {

            lastGoodFrequency = SP.getDouble(RileyLinkConst.Prefs.LastGoodDeviceFrequency, 0.0d);
            lastGoodFrequency = Math.round(lastGoodFrequency * 1000d) / 1000d;

            RileyLinkUtil.getRileyLinkServiceData().lastGoodFrequency = lastGoodFrequency;

//            if (RileyLinkUtil.getRileyLinkTargetFrequency() == null) {
//                String pumpFrequency = SP.getString(MedtronicConst.Prefs.PumpFrequency, null);
//            }
        } else {
            lastGoodFrequency = RileyLinkUtil.getRileyLinkServiceData().lastGoodFrequency;
        }

        if ((lastGoodFrequency > 0.0d)
            && RileyLinkUtil.getRileyLinkCommunicationManager().isValidFrequency(lastGoodFrequency)) {

            RileyLinkUtil.setServiceState(RileyLinkServiceState.RileyLinkReady);

            if (L.isEnabled(L.PUMPCOMM))
                LOG.info("Setting radio frequency to {} MHz", lastGoodFrequency);

            RileyLinkUtil.getRileyLinkCommunicationManager().setRadioFrequencyForPump(lastGoodFrequency);

            boolean foundThePump = RileyLinkUtil.getRileyLinkCommunicationManager().tryToConnectToDevice();

            if (foundThePump) {
                RileyLinkUtil.setServiceState(RileyLinkServiceState.PumpConnectorReady);
            } else {
                RileyLinkUtil.setServiceState(RileyLinkServiceState.PumpConnectorError,
                    RileyLinkError.NoContactWithDevice);
                RileyLinkUtil.sendBroadcastMessage(RileyLinkConst.IPC.MSG_PUMP_tunePump);
            }

        } else {
            RileyLinkUtil.sendBroadcastMessage(RileyLinkConst.IPC.MSG_PUMP_tunePump);
        }
    }
}
