package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks;

import javax.inject.Inject;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.interfaces.ActivePluginProvider;
import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkPumpDevice;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.data.ServiceTransport;
import info.nightscout.androidaps.plugins.pump.medtronic.events.EventRefreshButtonState;

/**
 * Created by geoff on 7/16/16.
 */
public class ResetRileyLinkConfigurationTask extends PumpTask {

    @Inject ActivePluginProvider activePlugin;
    @Inject RxBusWrapper rxBus;

    public ResetRileyLinkConfigurationTask(HasAndroidInjector injector) {
        super(injector);
    }


    public ResetRileyLinkConfigurationTask(HasAndroidInjector injector, ServiceTransport transport) {
        super(injector, transport);
    }


    @Override
    public void run() {
        RileyLinkPumpDevice pumpAbstract = (RileyLinkPumpDevice) activePlugin.getActivePump();

        rxBus.send(new EventRefreshButtonState(false));

        pumpAbstract.setIsBusy(true);
        pumpAbstract.resetRileyLinkConfiguration();
        pumpAbstract.setIsBusy(false);

        rxBus.send(new EventRefreshButtonState(true));
    }


}
