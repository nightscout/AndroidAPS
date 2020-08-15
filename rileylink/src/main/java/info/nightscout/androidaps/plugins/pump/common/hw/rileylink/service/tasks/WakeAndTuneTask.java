package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks;

import javax.inject.Inject;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.interfaces.ActivePluginProvider;
import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.plugins.pump.common.events.EventRefreshButtonState;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkPumpDevice;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.data.ServiceTransport;

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
        RileyLinkPumpDevice pumpDevice = (RileyLinkPumpDevice)activePlugin.getActivePump();
        rxBus.send(new EventRefreshButtonState(false));
        pumpDevice.setIsBusy(true);
        pumpDevice.doTuneUpDevice();
        pumpDevice.setIsBusy(false);
        rxBus.send(new EventRefreshButtonState(true));
    }
}
