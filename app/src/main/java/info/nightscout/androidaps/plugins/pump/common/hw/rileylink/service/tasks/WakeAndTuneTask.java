package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks;

import javax.inject.Inject;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.interfaces.ActivePluginProvider;
import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.plugins.pump.common.PumpPluginAbstract;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.data.ServiceTransport;
import info.nightscout.androidaps.plugins.pump.medtronic.MedtronicPumpPlugin;
import info.nightscout.androidaps.plugins.pump.medtronic.events.EventRefreshButtonState;

/**
 * Created by geoff on 7/16/16.
 */
public class WakeAndTuneTask extends PumpTask {

    @Inject ActivePluginProvider activePlugin;
    @Inject RxBusWrapper rxBus;

    private static final String TAG = "WakeAndTuneTask";


    public WakeAndTuneTask(HasAndroidInjector injector) {
        super(injector);
    }


    public WakeAndTuneTask(HasAndroidInjector injector, ServiceTransport transport) {
        super(injector, transport);
    }


    @Override
    public void run() {
        PumpPluginAbstract pump = (PumpPluginAbstract) activePlugin.getActivePump();
        rxBus.send(new EventRefreshButtonState(false));
        MedtronicPumpPlugin.isBusy = true;
        pump.doTuneUpDevice();
        MedtronicPumpPlugin.isBusy = false;
        rxBus.send(new EventRefreshButtonState(true));
    }
}
