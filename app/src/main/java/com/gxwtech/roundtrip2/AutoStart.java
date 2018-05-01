package com.gxwtech.roundtrip2;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.gxwtech.roundtrip2.CommunicationService.CommunicationService;

/**
 * Created by Tim on 07/06/2016.
 * Receives BOOT_COMPLETED Intent and starts service
 */
public class AutoStart extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {

        context.startService(new Intent(context, CommunicationService.class));

    }
}