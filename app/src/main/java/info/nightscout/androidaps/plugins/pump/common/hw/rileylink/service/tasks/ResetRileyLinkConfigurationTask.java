package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks;

import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.data.ServiceTransport;
import info.nightscout.androidaps.plugins.pump.medtronic.MedtronicPumpPlugin;
import info.nightscout.androidaps.plugins.pump.medtronic.events.EventRefreshButtonState;
import info.nightscout.androidaps.plugins.pump.medtronic.service.RileyLinkMedtronicService;
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedtronicUtil;
import info.nightscout.androidaps.plugins.pump.omnipod.OmnipodPumpPlugin;
import info.nightscout.androidaps.plugins.pump.omnipod.service.RileyLinkOmnipodService;
import info.nightscout.androidaps.plugins.pump.omnipod.util.OmnipodUtil;

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
        RxBus.Companion.getINSTANCE().send(new EventRefreshButtonState(false));
        if (MedtronicUtil.isMedtronicPump()) {
            MedtronicPumpPlugin.isBusy = true;
            RileyLinkMedtronicService.getInstance().resetRileyLinkConfiguration();
            MedtronicPumpPlugin.isBusy = false;
        } else if (OmnipodUtil.isOmnipodEros()) {
            OmnipodPumpPlugin.isBusy = true;
            RileyLinkOmnipodService.getInstance().resetRileyLinkConfiguration();
            OmnipodPumpPlugin.isBusy = false;
        }
        RxBus.Companion.getINSTANCE().send(new EventRefreshButtonState(true));  
    }

}
