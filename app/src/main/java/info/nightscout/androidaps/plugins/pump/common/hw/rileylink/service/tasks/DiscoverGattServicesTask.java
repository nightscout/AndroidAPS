package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkUtil;

/**
 * Created by geoff on 7/9/16.
 */
public class DiscoverGattServicesTask extends ServiceTask {

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
            RileyLinkUtil.getInstance().getRileyLinkBLE().connectGatt();

        RileyLinkUtil.getInstance().getRileyLinkBLE().discoverServices();
    }
}
