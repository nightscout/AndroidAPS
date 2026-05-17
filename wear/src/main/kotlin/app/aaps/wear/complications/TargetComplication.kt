package app.aaps.wear.complications

import android.app.PendingIntent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import app.aaps.core.interfaces.logging.LTag
import app.aaps.wear.R
import dagger.android.AndroidInjection

/**
 * Target / Temp Target Complication
 *
 * Shows the active target or temp target with remaining duration.
 * - No TT: profile target range e.g. "6.6-7.0" with target icon
 * - TT active: Text: TT value e.g. "6.6", Title: remaining duration e.g. "1h 30'"
 */
class TargetComplication : ModernBaseComplicationProviderService() {

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
        val statusData = data.statusData

        return when (type) {
            ComplicationType.SHORT_TEXT -> {
                val hasTT = statusData.tempTargetDuration >= 0
                val builder = ShortTextComplicationData.Builder(
                    text = PlainComplicationText.Builder(text = statusData.tempTarget).build(),
                    contentDescription = PlainComplicationText.Builder(text = "Target").build()
                )

                if (hasTT) {
                    builder.setTitle(PlainComplicationText.Builder(text = formatDuration(statusData.tempTargetDuration)).build())
                } else {
                    builder.setMonochromaticImage(MonochromaticImage.Builder(image = Icon.createWithResource(this, R.drawable.ic_tt)).build())
                }

                builder.setTapAction(complicationPendingIntent).build()
            }

            else -> {
                aapsLogger.warn(LTag.WEAR, "Unexpected complication type $type")
                null
            }
        }
    }

    private fun formatDuration(durationMs: Long): String {
        val h = getString(R.string.hour_short)
        val totalMinutes = (durationMs / 60_000).toInt().coerceAtLeast(0)
        return if (totalMinutes >= 60) {
            val hours = totalMinutes / 60
            val minutes = totalMinutes % 60
            if (minutes == 0) "$hours$h" else "$hours$h ${minutes}'"
        } else {
            "$totalMinutes'"
        }
    }

    override fun getPreviewComplicationData(): app.aaps.wear.data.ComplicationData {
        return app.aaps.wear.data.ComplicationData(
            statusData = app.aaps.core.interfaces.rx.weardata.EventData.Status(
                dataset = 0,
                externalStatus = "",
                iobSum = "",
                iobDetail = "",
                cob = "",
                currentBasal = "",
                battery = "",
                rigBattery = "",
                openApsStatus = -1L,
                bgi = "",
                batteryLevel = 0,
                tempTarget = "6.6",
                tempTargetLevel = 2,
                tempTargetDuration = 90 * 60_000L,
                reservoirString = "",
                reservoir = 0.0,
                reservoirLevel = 0
            ),
            lastUpdateTimestamp = System.currentTimeMillis()
        )
    }

    override fun getProviderCanonicalName(): String = TargetComplication::class.java.canonicalName!!
    override fun getComplicationAction(): ComplicationAction = ComplicationAction.TEMP_TARGET
}
