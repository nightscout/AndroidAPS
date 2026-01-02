package app.aaps.wear.complications

import android.app.PendingIntent
import android.graphics.drawable.Icon
import androidx.annotation.DrawableRes
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.MonochromaticImageComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import androidx.wear.watchface.complications.data.SmallImageType
import app.aaps.core.interfaces.logging.LTag
import app.aaps.wear.R
import dagger.android.AndroidInjection
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Uploader Battery Complication
 *
 * Shows phone battery level for uploader device
 *
 */
class UploaderBatteryComplication : ModernBaseComplicationProviderService() {

    // Not derived from DaggerService, do injection here
    override fun onCreate() {
        AndroidInjection.inject(this)
        super.onCreate()
    }

    override fun buildComplicationData(
        type: ComplicationType,
        data: app.aaps.wear.data.ComplicationData,
        complicationPendingIntent: PendingIntent
    ): ComplicationData? {
        aapsLogger.debug(LTag.WEAR, "UploaderBatteryComplication.buildComplicationData: type=$type")
        val statusData = data.statusData

        @DrawableRes var batteryIcon = R.drawable.ic_battery_unknown
        @DrawableRes var burnInBatteryIcon = R.drawable.ic_battery_unknown_burnin
        var level = 0
        var levelStr = "???"

        if (statusData.battery.matches(Regex("^[0-9]+$"))) {
            try {
                level = statusData.battery.toInt()
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
                aapsLogger.error(LTag.WEAR, "Cannot parse battery level of: ${statusData.battery}")
            }
        }

        return when (type) {
            ComplicationType.RANGED_VALUE        -> {
                val builder = RangedValueComplicationData.Builder(
                    value = level.toFloat(),
                    min = 0f,
                    max = 100f,
                    contentDescription = PlainComplicationText.Builder(text = "Battery $levelStr").build()
                )
                    .setText(PlainComplicationText.Builder(text = levelStr).build())
                // Try to add icon, but skip if context not initialized (e.g., during preview)
                try {
                    builder.setMonochromaticImage(MonochromaticImage.Builder(image = Icon.createWithResource(this, batteryIcon)).build())
                } catch (_: Exception) {
                    // Icon creation failed - likely preview mode, continue without icon
                }
                builder.setTapAction(complicationPendingIntent).build()
            }

            ComplicationType.SHORT_TEXT          -> {
                val builder = ShortTextComplicationData.Builder(
                    text = PlainComplicationText.Builder(text = levelStr).build(),
                    contentDescription = PlainComplicationText.Builder(text = "Battery $levelStr").build()
                )
                // Try to add icon, but skip if context not initialized (e.g., during preview)
                try {
                    builder.setMonochromaticImage(MonochromaticImage.Builder(image = Icon.createWithResource(this, batteryIcon)).build())
                } catch (_: Exception) {
                    // Icon creation failed - likely preview mode, continue without icon
                }
                builder.setTapAction(complicationPendingIntent).build()
            }

            ComplicationType.MONOCHROMATIC_IMAGE -> {
                // New AndroidX type for simple icon-only complications
                val builder = MonochromaticImageComplicationData.Builder(
                    monochromaticImage = MonochromaticImage.Builder(image = Icon.createWithResource(this, burnInBatteryIcon)).build(),
                    contentDescription = PlainComplicationText.Builder(text = "Battery $levelStr").build()
                )
                builder.setTapAction(complicationPendingIntent).build()
            }

            ComplicationType.SMALL_IMAGE         -> {
                SmallImageComplicationData.Builder(
                    smallImage = SmallImage.Builder(image = Icon.createWithResource(this, batteryIcon), type = SmallImageType.ICON).build(),
                    contentDescription = PlainComplicationText.Builder(text = "Battery").build()
                )
                    .setTapAction(complicationPendingIntent)
                    .build()
            }

            else                               -> {
                aapsLogger.warn(LTag.WEAR, "Unexpected complication type $type")
                null
            }
        }
    }

    override fun getProviderCanonicalName(): String = UploaderBatteryComplication::class.java.canonicalName!!
}
