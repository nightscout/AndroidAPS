package com.gxwtech.roundtrip2.RoundtripService.Tasks;

import com.gxwtech.roundtrip2.RoundtripService.RoundtripService;
import com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpMessage;
import com.gxwtech.roundtrip2.ServiceData.ServiceResult;
import com.gxwtech.roundtrip2.ServiceData.ServiceTransport;

/**
 * Created by geoff on 7/10/16.
 */
public class ReadBolusWizardCarbProfileTask extends PumpTask {
    public ReadBolusWizardCarbProfileTask() { super(); }
    public ReadBolusWizardCarbProfileTask(ServiceTransport transport) {
        super(transport);
    }

    @Override
    public void run() {
        PumpMessage msg = RoundtripService.getInstance().pumpManager.getBolusWizardCarbProfile();
        ServiceResult result = getServiceTransport().getServiceResult();
        // interpret msg here.
        getServiceTransport().setServiceResult(result);
    }
}
