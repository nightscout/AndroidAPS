package app.aaps.wear.complications

import android.app.PendingIntent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import app.aaps.core.interfaces.logging.LTag
import dagger.android.AndroidInjection

/**
 * Long Status Flipped Complication
 *
 * Shows comprehensive glucose and status information in long text format (flipped layout)
 * Title: COB, IOB, and basal rate
 * Text: Glucose value, arrow, delta, and time
 *
 */
class LongStatusFlippedComplication : ModernBaseComplicationProviderService() {

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
        return when (type) {
            ComplicationType.LONG_TEXT      -> {
                // Pass EventData arrays directly to DisplayFormat
                val singleBg = arrayOf(data.bgData, data.bgData1, data.bgData2)
                val status = arrayOf(data.statusData, data.statusData1, data.statusData2)

                val glucoseLine = displayFormat.longGlucoseLine(singleBg, 0)
                val detailsLine = displayFormat.longDetailsLine(status, 0)

                LongTextComplicationData.Builder(
                    text = PlainComplicationText.Builder(text = glucoseLine).build(),
                    contentDescription = PlainComplicationText.Builder(text = "Status: $detailsLine $glucoseLine").build()
                )
                    .setTitle(PlainComplicationText.Builder(text = detailsLine).build())
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