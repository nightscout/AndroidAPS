package info.nightscout.androidaps.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.BatteryManager;

import info.nightscout.androidaps.events.EventChargingState;
import info.nightscout.androidaps.plugins.bus.RxBus;

public class ChargingStateReceiver extends BroadcastReceiver {

    private static EventChargingState lastEvent;

    @Override
    public void onReceive(Context context, Intent intent) {
        EventChargingState event = grabChargingState(context);

        if (event != null)
            RxBus.INSTANCE.send(event);
        lastEvent = event;
    }

    public static EventChargingState grabChargingState(Context context) {
        BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);

        if (bm == null)
            return new EventChargingState(false);

        int status = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS);
        boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL;

        EventChargingState event = new EventChargingState(isCharging);
        return event;
    }

    static public boolean isCharging() {
        return lastEvent != null && lastEvent.isCharging();
    }

    static public EventChargingState getLastEvent() {
        return lastEvent;
    }
}