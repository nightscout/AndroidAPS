package app.aaps.wear.complications

import android.app.PendingIntent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import app.aaps.core.interfaces.logging.LTag

/**
 * Long Status Complication
 *
 * Shows comprehensive glucose and status information as a single long text line:
 * COB, IOB, and basal rate, then glucose value, arrow, auto-updating time, and delta.
 *
 * Everything lives in the text field (no title) so the separator between status and
 * glucose is ours — watch faces join text and title with their own separator (often "/").
 *
 */
class LongStatusComplication : ModernBaseComplicationProviderService() {


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
                    text = buildCountUpText(bgData.timeStamp, "$detailsLine$sep$glucose ^1 ${bgData.delta}"),
                    contentDescription = PlainComplicationText.Builder(text = "Status: $detailsLine $glucose ${bgData.delta}").build()
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

    override fun getProviderCanonicalName(): String = LongStatusComplication::class.java.canonicalName!!
}