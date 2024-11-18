@file:Suppress("DEPRECATION")

package app.aaps.wear.complications

import android.app.PendingIntent
import android.graphics.drawable.Icon
import android.support.wearable.complications.ComplicationData
import android.support.wearable.complications.ComplicationText
import androidx.annotation.DrawableRes
import app.aaps.core.interfaces.logging.LTag
import app.aaps.wear.R
import app.aaps.wear.data.RawDisplayData
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/*
 * Created by dlvoy on 2019-11-12
 */
class UploaderBatteryComplication : BaseComplicationProviderService() {

    override fun buildComplicationData(dataType: Int, raw: RawDisplayData, complicationPendingIntent: PendingIntent): ComplicationData? {
        var complicationData: ComplicationData? = null
        @DrawableRes var batteryIcon = R.drawable.ic_battery_unknown
        @DrawableRes var burnInBatteryIcon = R.drawable.ic_battery_unknown_burnin
        var level = 0
        var levelStr = "???"
        if (raw.status[0].battery.matches(Regex("^[0-9]+$"))) {
            try {
                level = raw.status[0].battery.toInt()
                level = max(min(level, 100), 0)
                levelStr = "$level%"
                var iconNo = floor(level / 10.0).toInt()
                if (level > 95) {
                    iconNo = 10
                }
                batteryIcon = when (iconNo) {
                    10   -> R.drawable.ic_battery_charging_wireless
                    9    -> R.drawable.ic_battery_charging_wireless_90
                    8    -> R.drawable.ic_battery_charging_wireless_80
                    7    -> R.drawable.ic_battery_charging_wireless_70
                    6    -> R.drawable.ic_battery_charging_wireless_60
                    5    -> R.drawable.ic_battery_charging_wireless_50
                    4    -> R.drawable.ic_battery_charging_wireless_40
                    3    -> R.drawable.ic_battery_charging_wireless_30
                    2    -> R.drawable.ic_battery_charging_wireless_20
                    1    -> R.drawable.ic_battery_charging_wireless_10
                    0    -> R.drawable.ic_battery_alert_variant_outline
                    else -> R.drawable.ic_battery_charging_wireless_outline
                }
                burnInBatteryIcon = when (iconNo) {
                    10   -> R.drawable.ic_battery_charging_wireless_burnin
                    9    -> R.drawable.ic_battery_charging_wireless_90_burnin
                    8    -> R.drawable.ic_battery_charging_wireless_80_burnin
                    7    -> R.drawable.ic_battery_charging_wireless_70_burnin
                    6    -> R.drawable.ic_battery_charging_wireless_60_burnin
                    5    -> R.drawable.ic_battery_charging_wireless_50_burnin
                    4    -> R.drawable.ic_battery_charging_wireless_40_burnin
                    3    -> R.drawable.ic_battery_charging_wireless_30_burnin
                    2    -> R.drawable.ic_battery_charging_wireless_20_burnin
                    1    -> R.drawable.ic_battery_charging_wireless_10_burnin
                    0    -> R.drawable.ic_battery_alert_variant_outline
                    else -> R.drawable.ic_battery_charging_wireless_outline
                }
            } catch (_: NumberFormatException) {
                aapsLogger.error(LTag.WEAR, "Cannot parse battery level of: " + raw.status[0].battery)
            }
        }
        when (dataType) {
            ComplicationData.TYPE_RANGED_VALUE -> {
                val builder = ComplicationData.Builder(ComplicationData.TYPE_RANGED_VALUE)
                    .setMinValue(0f)
                    .setMaxValue(100f)
                    .setValue(level.toFloat())
                    .setShortText(ComplicationText.plainText(levelStr))
                    .setIcon(Icon.createWithResource(this, batteryIcon))
                    .setBurnInProtectionIcon(Icon.createWithResource(this, burnInBatteryIcon))
                    .setTapAction(complicationPendingIntent)
                complicationData = builder.build()
            }

            ComplicationData.TYPE_SHORT_TEXT   -> {
                val builder = ComplicationData.Builder(ComplicationData.TYPE_SHORT_TEXT)
                    .setShortText(ComplicationText.plainText(levelStr))
                    .setIcon(Icon.createWithResource(this, batteryIcon))
                    .setBurnInProtectionIcon(Icon.createWithResource(this, burnInBatteryIcon))
                    .setTapAction(complicationPendingIntent)
                complicationData = builder.build()
            }

            ComplicationData.TYPE_ICON         -> {
                val builder = ComplicationData.Builder(ComplicationData.TYPE_ICON)
                    .setIcon(Icon.createWithResource(this, batteryIcon))
                    .setBurnInProtectionIcon(Icon.createWithResource(this, burnInBatteryIcon))
                    .setTapAction(complicationPendingIntent)
                complicationData = builder.build()
            }

            else                               -> {
                aapsLogger.warn(LTag.WEAR, "Unexpected complication type $dataType")
            }
        }
        return complicationData
    }

    override fun getProviderCanonicalName(): String = UploaderBatteryComplication::class.java.canonicalName!!
    override fun getComplicationAction(): ComplicationAction = ComplicationAction.STATUS
}