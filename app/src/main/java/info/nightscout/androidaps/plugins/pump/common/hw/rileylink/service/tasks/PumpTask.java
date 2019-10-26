package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks;

import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.data.ServiceTransport;

/**
 * Created by geoff on 7/10/16.
 */
public class PumpTask extends ServiceTask {

    public PumpTask() {
        super();
    }


    public PumpTask(ServiceTransport transport) {
        super(transport);
    }
}
