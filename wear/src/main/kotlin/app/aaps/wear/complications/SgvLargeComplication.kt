package app.aaps.wear.complications

import android.app.PendingIntent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.weardata.EventData

/**
 * SGV Large Complication
 *
 * Shows BG value as large as possible with trend and auto-updating age.
 * Display format: "6.8" (large) with "3m ↗" above
 * - No delta — trend arrow conveys direction, age conveys freshness
 * - Time auto-updates every minute (battery efficient)
 */
class SgvLargeComplication : ModernBaseComplicationProviderService() {

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
        val titleText = buildCountUpText(bgData.timeStamp, "^1 ${bgData.slopeArrow}\uFE0E")

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
