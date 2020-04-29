package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks;

import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.data.ServiceTransport;
import info.nightscout.androidaps.plugins.pump.medtronic.MedtronicPumpPlugin;
import info.nightscout.androidaps.plugins.pump.medtronic.events.EventRefreshButtonState;
import info.nightscout.androidaps.plugins.pump.medtronic.service.RileyLinkMedtronicService;

/**
 * Created by geoff on 7/16/16.
 */
public class ResetRileyLinkConfigurationTask extends PumpTask {

    private static final String TAG = "ResetRileyLinkTask";


    public ResetRileyLinkConfigurationTask() {
    }


    public ResetRileyLinkConfigurationTask(ServiceTransport transport) {
        super(transport);
    }


    @Override
    public void run() {
	// TODO Omnipod refactor this	
	if (MedtronicUtil.isMedtronicPump()) {
        RxBus.Companion.getINSTANCE().send(new EventRefreshButtonState(false));
        MedtronicPumpPlugin.isBusy = true;
        RileyLinkMedtronicService.getInstance().resetRileyLinkConfiguration();
        MedtronicPumpPlugin.isBusy = false;
        RxBus.Companion.getINSTANCE().send(new EventRefreshButtonState(true));
    } else if (OmnipodUtil.isOmnipodEros()) {
            OmnipodPumpPlugin.isBusy = true;
            RileyLinkOmnipodService.getInstance().resetRileyLinkConfiguration();
            OmnipodPumpPlugin.isBusy = false;
        }

	RxBus.Companion.getINSTANCE().send(new EventRefreshButtonState(true));
}
