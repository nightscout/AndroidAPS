package app.aaps.wear.complications

import android.app.PendingIntent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import app.aaps.core.interfaces.logging.LTag

/**
 * Long Status Flipped Complication
 *
 * Shows comprehensive glucose and status information as a single long text line
 * (flipped layout): glucose value, arrow, auto-updating time, and delta, then
 * COB, IOB, and basal rate.
 *
 * Everything lives in the text field (no title) so the separator between glucose and
 * status is ours — watch faces join text and title with their own separator (often "/").
 *
 */
class LongStatusFlippedComplication : ModernBaseComplicationProviderService() {


    override fun buildComplicationData(
        type: ComplicationType,
        data: app.aaps.wear.data.ComplicationData,
        complicationPendingIntent: PendingIntent
    ): ComplicationData? {
        return when (type) {
            ComplicationType.LONG_TEXT      -> {
                // Pass EventData arrays directly to DisplayFormat
                val status = arrayOf(data.statusData, data.statusData1, data.statusData2)

                val bgData = data.bgData
                val glucose = bgData.sgvString + bgData.slopeArrow
                val detailsLine = displayFormat.longDetailsLine(status, 0)
                val sep = displayFormat.fieldSeparator()

                LongTextComplicationData.Builder(
                    // Age + delta formatted like SgvComplication's title ("5m +0.1")
                    text = buildCountUpText(bgData.timeStamp, "$glucose ^1 ${bgData.delta}$sep$detailsLine"),
                    contentDescription = PlainComplicationText.Builder(text = "Status: $glucose ${bgData.delta} $detailsLine").build()
                )
                    .setTapAction(complicationPendingIntent)
                    .build()
            }

            else                            -> {
                aapsLogger.warn(LTag.WEAR, "Unexpected complication type $type")
                null
            }
        }
    }

    override fun getProviderCanonicalName(): String = LongStatusFlippedComplication::class.java.canonicalName!!
}