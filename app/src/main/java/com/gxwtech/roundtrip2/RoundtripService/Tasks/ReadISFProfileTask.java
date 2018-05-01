package com.gxwtech.roundtrip2.RoundtripService.Tasks;

import android.os.Bundle;

import com.gxwtech.roundtrip2.RoundtripService.RoundtripService;
import com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpData.ISFTable;
import com.gxwtech.roundtrip2.RoundtripService.medtronic.TimeFormat;
import com.gxwtech.roundtrip2.ServiceData.ServiceResult;
import com.gxwtech.roundtrip2.ServiceData.ServiceTransport;

/**
 * Created by geoff on 7/10/16.
 */
public class ReadISFProfileTask extends PumpTask {
    public ReadISFProfileTask() {}
    public ReadISFProfileTask(ServiceTransport transport) { super(transport); }
    @Override
    public void preOp() {
    }

    @Override
    public void run() {
        ISFTable table = RoundtripService.getInstance().pumpManager.getPumpISFProfile();
        ServiceResult result = getServiceTransport().getServiceResult();
        if (table.isValid()) {
            // convert from ISFTable to ISFProfile
            Bundle map = result.getMap();
            map.putIntArray("times", table.getTimes());
            map.putFloatArray("rates", table.getRates());
            map.putString("ValidDate", TimeFormat.standardFormatter().print(table.getValidDate()));
            result.setMap(map);
            result.setResultOK();
            getServiceTransport().setServiceResult(result);
        }
    }

}
