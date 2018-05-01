package com.gxwtech.roundtrip2.RoundtripService.Tasks;

import com.gxwtech.roundtrip2.RoundtripService.RoundtripService;

/**
 * Created by geoff on 7/9/16.
 */
public class DiscoverGattServicesTask extends ServiceTask {
    public DiscoverGattServicesTask() {}

    @Override
    public void run() {
        RoundtripService.getInstance().rileyLinkBLE.discoverServices();
    }
}
