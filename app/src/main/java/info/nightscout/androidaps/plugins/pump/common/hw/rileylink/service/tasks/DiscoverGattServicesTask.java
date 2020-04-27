package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks;

import javax.inject.Inject;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkUtil;
import info.nightscout.androidaps.plugins.pump.medtronic.MedtronicPumpPlugin;

/**
 * Created by geoff on 7/9/16.
 */
public class DiscoverGattServicesTask extends ServiceTask {

    @Inject MedtronicPumpPlugin medtronicPumpPlugin;

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

        if (needToConnect)
            medtronicPumpPlugin.getRileyLinkService().getRileyLinkBLE().connectGatt();

        medtronicPumpPlugin.getRileyLinkService().getRileyLinkBLE().discoverServices();
    }
}
