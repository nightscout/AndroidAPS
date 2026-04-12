package app.aaps.wear.complications

import android.app.PendingIntent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import app.aaps.core.interfaces.logging.LTag
import app.aaps.wear.R
import dagger.android.AndroidInjection

/**
 * Basal / Temp Basal Rate + Target / Temp Target Complication
 *
 * Shows the current basal rate (or temp basal) and the active target:
 * - When a temp target is active: target value with remaining duration, e.g. "6.6 (1h 30')"
 * - When no temp target: profile target range, e.g. "6.6-7.0"
 *
 * Text (larger):  Target or TT with duration
 * Title (smaller): Basal rate with symbol
 */
class BrTtComplication : ModernBaseComplicationProviderService() {

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
                val targetText = formatTargetText(statusData.tempTarget, statusData.tempTargetDuration)
                val basalText = displayFormat.basalRateSymbol().trimEnd() + statusData.currentBasal.replaceFirst(" ", "")

                ShortTextComplicationData.Builder(
                    text = PlainComplicationText.Builder(text = targetText).build(),
                    contentDescription = PlainComplicationText.Builder(text = "Basal Rate, Target").build()
                )
                    .setTitle(PlainComplicationText.Builder(text = basalText).build())
                    .setTapAction(complicationPendingIntent)
                    .build()
            }

            else -> {
                aapsLogger.warn(LTag.WEAR, "Unexpected complication type $type")
                null
            }
        }
    }

    /**
     * Format the target display string.
     * When a TT is active (duration >= 0), appends the remaining duration: "6.6 (1h 30')"
     * Otherwise returns the profile target as-is: "6.6-7.0"
     */
    private fun formatTargetText(target: String, durationMs: Long): String {
        if (durationMs < 0) return target
        return "$target (${formatDuration(durationMs)})"
    }

    /**
     * Format remaining duration from milliseconds.
     * Examples: 90 min → "1h 30'", 45 min → "45'", 120 min → "2h"
     */
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
                currentBasal = "0.8U/h",
                battery = "",
                rigBattery = "",
                openApsStatus = -1L,
                bgi = "",
                batteryLevel = 0,
                tempTarget = "7.0",
                tempTargetLevel = 0,
                tempTargetDuration = 90 * 60_000L,
                reservoirString = "",
                reservoir = 0.0,
                reservoirLevel = 0
            ),
            lastUpdateTimestamp = System.currentTimeMillis()
        )
    }

    override fun getProviderCanonicalName(): String = BrTtComplication::class.java.canonicalName!!
}
