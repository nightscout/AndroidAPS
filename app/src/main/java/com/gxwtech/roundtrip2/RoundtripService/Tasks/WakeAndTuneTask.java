package com.gxwtech.roundtrip2.RoundtripService.Tasks;

import com.gxwtech.roundtrip2.RoundtripService.RoundtripService;
import com.gxwtech.roundtrip2.ServiceData.ServiceTransport;

/**
 * Created by geoff on 7/16/16.
 */
public class WakeAndTuneTask extends PumpTask {
    private static final String TAG = "WakeAndTuneTask";
    public WakeAndTuneTask() { }
    public WakeAndTuneTask(ServiceTransport transport) {
        super(transport);
    }

    @Override
    public void run() {
        RoundtripService.getInstance().pumpManager.wakeup(6);
        RoundtripService.getInstance().pumpManager.tuneForPump();
    }

}
