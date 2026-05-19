package app.aaps.wear.complications

import android.app.PendingIntent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.CountUpTimeReference
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.TimeDifferenceComplicationText
import androidx.wear.watchface.complications.data.TimeDifferenceStyle
import app.aaps.core.interfaces.logging.LTag
import dagger.android.AndroidInjection
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * SGV (Sensor Glucose Value) Complication
 *
 * Shows current blood glucose with arrow, auto-updating time, and delta
 * Display format: "6.8↘" with "5m +0.1" above (mmol/L) or "5m +1" (mg/dL)
 * - Time auto-updates every minute (battery efficient)
 * - Delta is static until new BG reading
 */
class SgvComplication : ModernBaseComplicationProviderService() {

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
        // Use dataset 0 (primary)
        val bgData = data.bgData
        aapsLogger.debug(LTag.WEAR, "SgvComplication building: dataset=0 sgv=${bgData.sgvString} arrow=${bgData.slopeArrow}")

        return when (type) {
            ComplicationType.SHORT_TEXT -> {
                buildShortTextComplication(bgData, complicationPendingIntent)
            }

            else -> {
                aapsLogger.warn(LTag.WEAR, "SgvComplication unexpected type: $type")
                null
            }
        }
    }

    private fun buildShortTextComplication(
        bgData: app.aaps.core.interfaces.rx.weardata.EventData.SingleBg,
        pendingIntent: PendingIntent
    ): ShortTextComplicationData {
        val mainText = bgData.sgvString + bgData.slopeArrow

        // Title: auto-updating time + delta (e.g., "5m +0.1")
        val titleText = buildDeltaAndTimeTitle(bgData)

        return ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(text = mainText).build(),
            contentDescription = PlainComplicationText.Builder(text = "Glucose $mainText").build()
        )
            .setTitle(titleText)
            .setTapAction(pendingIntent)
            .build()
    }

    /**
     * Build combined delta and time title (e.g., "5m +0.1" mmol/L or "5m +1" mg/dL)
     * Time auto-updates, delta is static until new BG reading
     * Uses ^1 placeholder which is replaced with the time difference
     */
    private fun buildDeltaAndTimeTitle(bgData: app.aaps.core.interfaces.rx.weardata.EventData.SingleBg): TimeDifferenceComplicationText =
        // SHORT_SINGLE_UNIT rounds to nearest; adding 30_000ms makes it round down instead,
        // matching CWF, AAPS overview, and BgGraphActivity
        TimeDifferenceComplicationText.Builder(
            style = TimeDifferenceStyle.SHORT_SINGLE_UNIT,
            countUpTimeReference = CountUpTimeReference(Instant.ofEpochMilli(bgData.timeStamp + 30_000L))
        )
            .setMinimumTimeUnit(TimeUnit.MINUTES)
            .setText("^1 ${bgData.delta}")
            .build()

    override fun getComplicationAction(): ComplicationAction = ComplicationAction.BG_GRAPH

    override fun getProviderCanonicalName(): String = SgvComplication::class.java.canonicalName!!
}
