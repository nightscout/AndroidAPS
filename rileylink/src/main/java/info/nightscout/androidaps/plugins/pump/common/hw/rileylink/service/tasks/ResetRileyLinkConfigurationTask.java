package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks;

import javax.inject.Inject;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.interfaces.ActivePluginProvider;
import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.plugins.pump.common.events.EventRefreshButtonState;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.RFSpy;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkPumpDevice;

/**
 * Created by andy on 9/16/18.
 */
public class ResetRileyLinkConfigurationTask extends PumpTask {

    @Inject ActivePluginProvider activePlugin;
    @Inject RxBusWrapper rxBus;
    @Inject RFSpy rfSpy;

    public ResetRileyLinkConfigurationTask(HasAndroidInjector injector) {
        super(injector);
    }

    @Override
    public void run() {

        if (!isRileyLinkDevice()) {
            return;
        }

        RileyLinkPumpDevice rileyLinkPumpDevice = (RileyLinkPumpDevice) activePlugin.getActivePump();

        rxBus.send(new EventRefreshButtonState(false));

        rileyLinkPumpDevice.setBusy(true);
        rfSpy.resetRileyLinkConfiguration();
        rileyLinkPumpDevice.setBusy(false);

        rxBus.send(new EventRefreshButtonState(true));
    }

}
