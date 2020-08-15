package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks;

import javax.inject.Inject;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.interfaces.ActivePluginProvider;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkPumpDevice;

/**
 * Created by geoff on 7/9/16.
 */
public class DiscoverGattServicesTask extends ServiceTask {

    @Inject ActivePluginProvider activePlugin;
    @Inject AAPSLogger aapsLogger;

    public boolean needToConnect = false;


    public DiscoverGattServicesTask(HasAndroidInjector injector) {
        super(injector);
    }


    public DiscoverGattServicesTask(HasAndroidInjector injector, boolean needToConnect) {
        super(injector);
        this.needToConnect = needToConnect;
    }


    @Override
    public void run() {

        if (activePlugin.getActivePump().model() == PumpType.Insulet_Omnipod_Dash) {
            // TODO different init classes, this can be probably removed later
            aapsLogger.warn(LTag.PUMPCOMM, "Discovery Gatt Services not implemeneted for Dash.");
            return;
        }

        RileyLinkPumpDevice pumpDevice = (RileyLinkPumpDevice) activePlugin.getActivePump();

        if (needToConnect) {
            pumpDevice.getRileyLinkService().getRileyLinkBLE().connectGatt();
        }

        pumpDevice.getRileyLinkService().getRileyLinkBLE().discoverServices();
    }
}