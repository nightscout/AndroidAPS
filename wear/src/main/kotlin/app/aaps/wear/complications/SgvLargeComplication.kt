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
import app.aaps.core.interfaces.rx.weardata.EventData
import dagger.android.AndroidInjection
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * SGV Large Complication
 *
 * Shows BG value as large as possible with trend and auto-updating age.
 * Display format: "6.8" (large) with "3m ↗" above
 * - No delta — trend arrow conveys direction, age conveys freshness
 * - Time auto-updates every minute (battery efficient)
 */
class SgvLargeComplication : ModernBaseComplicationProviderService() {

    override fun onCreate() {
        AndroidInjection.inject(this)
        super.onCreate()
    }

    override fun buildComplicationData(
        type: ComplicationType,
        data: app.aaps.wear.data.ComplicationData,
        complicationPendingIntent: PendingIntent
    ): ComplicationData? {
        val bgData = data.bgData
        aapsLogger.debug(LTag.WEAR, "SgvLargeComplication building: sgv=${bgData.sgvString} arrow=${bgData.slopeArrow}")

        return when (type) {
            ComplicationType.SHORT_TEXT -> buildShortTextComplication(bgData, complicationPendingIntent)
            else -> {
                aapsLogger.warn(LTag.WEAR, "SgvLargeComplication unexpected type: $type")
                null
            }
        }
    }

    private fun buildShortTextComplication(
        bgData: EventData.SingleBg,
        pendingIntent: PendingIntent
    ): ShortTextComplicationData {
        val titleText = TimeDifferenceComplicationText.Builder(
            style = TimeDifferenceStyle.SHORT_SINGLE_UNIT,
            countUpTimeReference = CountUpTimeReference(Instant.ofEpochMilli(bgData.timeStamp))
        )
            .setMinimumTimeUnit(TimeUnit.MINUTES)
            .setText("^1 ${bgData.slopeArrow}\uFE0E")
            .build()

        return ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(text = bgData.sgvString).build(),
            contentDescription = PlainComplicationText.Builder(text = "Glucose ${bgData.sgvString}").build()
        )
            .setTitle(titleText)
            .setTapAction(pendingIntent)
            .build()
    }

    override fun getComplicationAction(): ComplicationAction = ComplicationAction.BG_GRAPH

    override fun getProviderCanonicalName(): String = SgvLargeComplication::class.java.canonicalName!!
}
