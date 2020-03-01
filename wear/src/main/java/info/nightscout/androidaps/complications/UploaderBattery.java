package info.nightscout.androidaps.complications;

import android.app.PendingIntent;
import android.graphics.drawable.Icon;
import android.support.wearable.complications.ComplicationData;
import android.support.wearable.complications.ComplicationText;
import android.util.Log;

import androidx.annotation.DrawableRes;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.RawDisplayData;

/*
 * Created by dlvoy on 2019-11-12
 */
public class UploaderBattery extends BaseComplicationProviderService {

    private static final String TAG = UploaderBattery.class.getSimpleName();

    public ComplicationData buildComplicationData(int dataType, RawDisplayData raw, PendingIntent complicationPendingIntent) {

        ComplicationData complicationData = null;

        @DrawableRes int batteryIcon = R.drawable.ic_battery_unknown;
        @DrawableRes int burnInBatteryIcon = R.drawable.ic_battery_unknown_burnin;
        int level = 0;
        String levelStr = "???";

        if (raw.sUploaderBattery.matches("^[0-9]+$")) {
            try {
                level = Integer.parseInt(raw.sUploaderBattery);
                level = Math.max(Math.min(level, 100), 0);
                levelStr = level + "%";
                int iconNo = (int)Math.floor(level / 10.0);
                if (level > 95) {
                    iconNo = 10;
                }
                switch (iconNo) {
                    case 10: batteryIcon = R.drawable.ic_battery_charging_wireless; break;
                    case 9: batteryIcon = R.drawable.ic_battery_charging_wireless_90; break;
                    case 8: batteryIcon = R.drawable.ic_battery_charging_wireless_80; break;
                    case 7: batteryIcon = R.drawable.ic_battery_charging_wireless_70; break;
                    case 6: batteryIcon = R.drawable.ic_battery_charging_wireless_60; break;
                    case 5: batteryIcon = R.drawable.ic_battery_charging_wireless_50; break;
                    case 4: batteryIcon = R.drawable.ic_battery_charging_wireless_40; break;
                    case 3: batteryIcon = R.drawable.ic_battery_charging_wireless_30; break;
                    case 2: batteryIcon = R.drawable.ic_battery_charging_wireless_20; break;
                    case 1: batteryIcon = R.drawable.ic_battery_charging_wireless_10; break;
                    case 0: batteryIcon = R.drawable.ic_battery_alert_variant_outline; break;
                    default: batteryIcon = R.drawable.ic_battery_charging_wireless_outline;
                }

                switch (iconNo) {
                    case 10: burnInBatteryIcon = R.drawable.ic_battery_charging_wireless_burnin; break;
                    case 9: burnInBatteryIcon = R.drawable.ic_battery_charging_wireless_90_burnin; break;
                    case 8: burnInBatteryIcon = R.drawable.ic_battery_charging_wireless_80_burnin; break;
                    case 7: burnInBatteryIcon = R.drawable.ic_battery_charging_wireless_70_burnin; break;
                    case 6: burnInBatteryIcon = R.drawable.ic_battery_charging_wireless_60_burnin; break;
                    case 5: burnInBatteryIcon = R.drawable.ic_battery_charging_wireless_50_burnin; break;
                    case 4: burnInBatteryIcon = R.drawable.ic_battery_charging_wireless_40_burnin; break;
                    case 3: burnInBatteryIcon = R.drawable.ic_battery_charging_wireless_30_burnin; break;
                    case 2: burnInBatteryIcon = R.drawable.ic_battery_charging_wireless_20_burnin; break;
                    case 1: burnInBatteryIcon = R.drawable.ic_battery_charging_wireless_10_burnin; break;
                    case 0: burnInBatteryIcon = R.drawable.ic_battery_alert_variant_outline; break;
                    default: burnInBatteryIcon = R.drawable.ic_battery_charging_wireless_outline;
                }


            } catch (NumberFormatException ex){
                Log.e(TAG, "Cannot parse battery level of: " + raw.sUploaderBattery);
            }
        }

        if (dataType == ComplicationData.TYPE_RANGED_VALUE) {
            final ComplicationData.Builder builder = new ComplicationData.Builder(ComplicationData.TYPE_RANGED_VALUE)
                    .setMinValue(0)
                    .setMaxValue(100)
                    .setValue(level)
                    .setShortText(ComplicationText.plainText(levelStr))
                    .setIcon(Icon.createWithResource(this, batteryIcon))
                    .setBurnInProtectionIcon(Icon.createWithResource(this, burnInBatteryIcon))
                    .setTapAction(complicationPendingIntent);
            complicationData = builder.build();
        } else if (dataType == ComplicationData.TYPE_SHORT_TEXT) {
            final ComplicationData.Builder builder = new ComplicationData.Builder(ComplicationData.TYPE_SHORT_TEXT)
                    .setShortText(ComplicationText.plainText(levelStr))
                    .setIcon(Icon.createWithResource(this, batteryIcon))
                    .setBurnInProtectionIcon(Icon.createWithResource(this, burnInBatteryIcon))
                    .setTapAction(complicationPendingIntent);
            complicationData = builder.build();
        } else if (dataType == ComplicationData.TYPE_ICON) {
            final ComplicationData.Builder builder = new ComplicationData.Builder(ComplicationData.TYPE_ICON)
                    .setIcon(Icon.createWithResource(this, batteryIcon))
                    .setBurnInProtectionIcon(Icon.createWithResource(this, burnInBatteryIcon))
                    .setTapAction(complicationPendingIntent);
            complicationData = builder.build();
        } else {
            if (Log.isLoggable(TAG, Log.WARN)) {
                Log.w(TAG, "Unexpected complication type " + dataType);
            }
        }
        return complicationData;
    }

    @Override
    public String getProviderCanonicalName() {
        return UploaderBattery.class.getCanonicalName();
    }

    @Override
    public ComplicationAction getComplicationAction() {
        return ComplicationAction.STATUS;
    };
}
