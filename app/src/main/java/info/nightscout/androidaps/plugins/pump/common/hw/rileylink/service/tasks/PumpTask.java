package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.data.ServiceTransport;

/**
 * Created by geoff on 7/10/16.
 */
public class PumpTask extends ServiceTask {

    public PumpTask(HasAndroidInjector injector) {
        super(injector);
    }


    public PumpTask(HasAndroidInjector injector, ServiceTransport transport) {
        super(injector, transport);
    }
}
