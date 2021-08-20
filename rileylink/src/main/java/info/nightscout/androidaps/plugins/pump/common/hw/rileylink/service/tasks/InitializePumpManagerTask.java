package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks;

import android.content.Context;

import javax.inject.Inject;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.interfaces.ActivePluginProvider;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.common.ManufacturerType;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkCommunicationManager;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkConst;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkUtil;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RileyLinkTargetFrequency;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkError;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkPumpDevice;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkServiceState;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.RileyLinkServiceData;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.data.ServiceTransport;
import info.nightscout.androidaps.utils.Round;
import info.nightscout.androidaps.utils.sharedPreferences.SP;

/**
 * Created by geoff on 7/9/16.
 * <p>
 * This class is intended to be run by the Service, for the Service. Not intended for clients to run.
 */
public class InitializePumpManagerTask extends ServiceTask {

    @Inject AAPSLogger aapsLogger;
    //@Inject ActivePluginProvider activePlugin;
    @Inject SP sp;
    @Inject RileyLinkServiceData rileyLinkServiceData;
    @Inject RileyLinkUtil rileyLinkUtil;

    private final Context context;

    public InitializePumpManagerTask(HasAndroidInjector injector, Context context) {
        super(injector);
        this.context = context;
    }

    public InitializePumpManagerTask(HasAndroidInjector injector, Context context, ServiceTransport transport) {
        super(injector, transport);
        this.context = context;
    }

    @Override
    public void run() {

        if (!isRileyLinkDevice()) {
            return;
        }

        double lastGoodFrequency;

        if (rileyLinkServiceData.lastGoodFrequency == null) {

            lastGoodFrequency = sp.getDouble(RileyLinkConst.Prefs.LastGoodDeviceFrequency, 0.0d);
            lastGoodFrequency = Math.round(lastGoodFrequency * 1000d) / 1000d;

            rileyLinkServiceData.lastGoodFrequency = lastGoodFrequency;

//            if (RileyLinkUtil.getRileyLinkTargetFrequency() == null) {
//                String pumpFrequency = SP.getString(MedtronicConst.Prefs.PumpFrequency, null);
//            }
        } else {
            lastGoodFrequency = rileyLinkServiceData.lastGoodFrequency;
        }

        /* FIXME this can apparently crash:
            Fatal Exception: java.lang.ClassCastException
            info.nightscout.androidaps.plugins.pump.virtual.VirtualPumpPlugin
            cannot be cast to info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkPumpDevice
         */
        RileyLinkCommunicationManager<?> rileyLinkCommunicationManager = ((RileyLinkPumpDevice) activePlugin.getActivePump()).getRileyLinkService().getDeviceCommunicationManager();

        if (activePlugin.getActivePump().manufacturer() == ManufacturerType.Medtronic) {

            if ((lastGoodFrequency > 0.0d)
                    && rileyLinkCommunicationManager.isValidFrequency(lastGoodFrequency)) {

                rileyLinkServiceData.setRileyLinkServiceState(RileyLinkServiceState.RileyLinkReady);

                aapsLogger.info(LTag.PUMPBTCOMM, "Setting radio frequency to {} MHz", lastGoodFrequency);

                rileyLinkCommunicationManager.setRadioFrequencyForPump(lastGoodFrequency);

                boolean foundThePump = rileyLinkCommunicationManager.tryToConnectToDevice();

                if (foundThePump) {
                    rileyLinkServiceData.setRileyLinkServiceState(RileyLinkServiceState.PumpConnectorReady);
                } else {
                    rileyLinkServiceData.setServiceState(RileyLinkServiceState.PumpConnectorError,
                            RileyLinkError.NoContactWithDevice);
                    rileyLinkUtil.sendBroadcastMessage(RileyLinkConst.IPC.MSG_PUMP_tunePump, context);
                }

            } else {
                rileyLinkUtil.sendBroadcastMessage(RileyLinkConst.IPC.MSG_PUMP_tunePump, context);
            }
        } else {

            if (!Round.isSame(lastGoodFrequency, RileyLinkTargetFrequency.Omnipod.getScanFrequencies()[0])) {
                lastGoodFrequency = RileyLinkTargetFrequency.Omnipod.getScanFrequencies()[0];
                lastGoodFrequency = Math.round(lastGoodFrequency * 1000d) / 1000d;

                rileyLinkServiceData.lastGoodFrequency = lastGoodFrequency;
            }

            rileyLinkServiceData.setRileyLinkServiceState(RileyLinkServiceState.RileyLinkReady);
            rileyLinkServiceData.rileyLinkTargetFrequency = RileyLinkTargetFrequency.Omnipod; // TODO shouldn't be needed

            aapsLogger.info(LTag.PUMPBTCOMM, "Setting radio frequency to {} MHz", lastGoodFrequency);

            rileyLinkCommunicationManager.setRadioFrequencyForPump(lastGoodFrequency);

            rileyLinkServiceData.setRileyLinkServiceState(RileyLinkServiceState.PumpConnectorReady);

        }
    }

}
