package info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.service.tasks;

import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.service.data.ServiceTransport;
import info.nightscout.androidaps.plugins.PumpMedtronic.service.RileyLinkMedtronicService;

/**
 * Created by geoff on 7/16/16.
 */
public class WakeAndTuneTask extends PumpTask {
    private static final String TAG = "WakeAndTuneTask";


    public WakeAndTuneTask() {
    }


    public WakeAndTuneTask(ServiceTransport transport) {
        super(transport);
    }


    @Override
    public void run() {
        //RileyLinkMedtronicService.getInstance().pumpCommunicationManager.wakeup(6);
        RileyLinkMedtronicService.getInstance().pumpCommunicationManager.tuneForPump();
    }

}
