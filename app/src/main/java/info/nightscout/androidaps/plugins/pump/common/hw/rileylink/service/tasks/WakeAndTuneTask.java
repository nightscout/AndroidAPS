package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.data.ServiceTransport;
import info.nightscout.androidaps.plugins.pump.medtronic.MedtronicFragment;
import info.nightscout.androidaps.plugins.pump.medtronic.MedtronicPumpPlugin;
import info.nightscout.androidaps.plugins.pump.medtronic.events.EventRefreshButtonState;
import info.nightscout.androidaps.plugins.pump.medtronic.service.RileyLinkMedtronicService;

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
        MainApp.bus().post(new EventRefreshButtonState(false));
        MedtronicPumpPlugin.isBusy = true;
        RileyLinkMedtronicService.getInstance().doTuneUpDevice();
        MedtronicPumpPlugin.isBusy = false;
        MainApp.bus().post(new EventRefreshButtonState(true));
    }
}
