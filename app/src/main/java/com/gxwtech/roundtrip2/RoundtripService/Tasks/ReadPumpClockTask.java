package com.gxwtech.roundtrip2.RoundtripService.Tasks;

import android.util.Log;

import com.gxwtech.roundtrip2.RoundtripService.RoundtripService;
import com.gxwtech.roundtrip2.ServiceData.ReadPumpClockResult;
import com.gxwtech.roundtrip2.ServiceData.ServiceTransport;

/**
 * Created by geoff on 7/9/16.
 */
public class ReadPumpClockTask extends PumpTask {
    private static final String TAG = "ReadPumpClockTask";
    public ReadPumpClockTask() { }
    public ReadPumpClockTask(ServiceTransport transport) {
        super(transport);
    }

    @Override
    public void run() {
        ReadPumpClockResult pumpResponse = RoundtripService.getInstance().pumpManager.getPumpRTC();
        if (pumpResponse != null) {
            Log.i(TAG, "ReadPumpClock: " + pumpResponse.getTimeString());
        } else {
            Log.e(TAG, "handleServiceCommand(" + mTransport.getOriginalCommandName() + ") pumpResponse is null");
        }
        getServiceTransport().setServiceResult(pumpResponse);
    }
}
