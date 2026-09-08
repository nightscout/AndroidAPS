package app.aaps.wear.complications

import android.app.PendingIntent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.weardata.EventData

/**
 * SGV (Sensor Glucose Value) Complication
 *
 * Shows current blood glucose with arrow, auto-updating time, and delta
 * Display format: "6.8↘" with "5m +0.1" above (mmol/L) or "5m +1" (mg/dL)
 * - Time auto-updates every minute (battery efficient)
 * - Delta is static until new BG reading
 */
class SgvComplication : ModernBaseComplicationProviderService() {


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
        bgData: EventData.SingleBg,
        pendingIntent: PendingIntent
    ): ShortTextComplicationData {
        val mainText = bgData.sgvString + bgData.slopeArrow

        // Title: auto-updating time + delta (e.g., "5m +0.1")
        val titleText = buildCountUpText(bgData.timeStamp, "^1 ${bgData.delta}")

        return ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(text = mainText).build(),
            contentDescription = PlainComplicationText.Builder(text = "Glucose $mainText").build()
        )
            .setTitle(titleText)
            .setTapAction(pendingIntent)
            .build()
    }

    override fun getComplicationAction(): ComplicationAction = ComplicationAction.LOOP_STATUS

    override fun getProviderCanonicalName(): String = SgvComplication::class.java.canonicalName!!
}
