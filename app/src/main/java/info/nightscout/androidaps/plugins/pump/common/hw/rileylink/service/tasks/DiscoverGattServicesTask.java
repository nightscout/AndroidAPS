package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks;

import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkUtil;

/**
 * Created by geoff on 7/9/16.
 */
public class DiscoverGattServicesTask extends ServiceTask {

    public boolean needToConnect = false;


    public DiscoverGattServicesTask() {
    }


    public DiscoverGattServicesTask(boolean needToConnect) {
        this.needToConnect = needToConnect;
    }


    @Override
    public void run() {

        if (needToConnect)
            RileyLinkUtil.getRileyLinkBLE().connectGatt();

        RileyLinkUtil.getRileyLinkBLE().discoverServices();
    }
}
