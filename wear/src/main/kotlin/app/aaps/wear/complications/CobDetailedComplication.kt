package app.aaps.wear.complications

import android.app.PendingIntent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import app.aaps.core.interfaces.logging.LTag
import dagger.android.AndroidInjection

/**
 * COB Detailed Complication
 *
 * Shows detailed carbs on board (COB) information
 * Displays both total COB and additional detail if space permits
 * Tap action opens carb/wizard dialog
 *
 */
class CobDetailedComplication : ModernBaseComplicationProviderService() {

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
            ComplicationType.SHORT_TEXT      -> {
                // Pass EventData arrays directly to DisplayFormat
                val status = arrayOf(data.statusData, data.statusData1, data.statusData2)

                val cob = displayFormat.detailedCob(status, 0)
                val builder = ShortTextComplicationData.Builder(
                    text = PlainComplicationText.Builder(text = cob.first).build(),
                    contentDescription = PlainComplicationText.Builder(text = "COB ${cob.first}").build()
                )
                    .setTapAction(complicationPendingIntent)
                if (cob.second.isNotEmpty()) {
                    builder.setTitle(PlainComplicationText.Builder(text = cob.second).build())
                }
                builder.build()
            }

            else                             -> {
                aapsLogger.warn(LTag.WEAR, "Unexpected complication type $type")
                null
            }
        }
    }

    override fun getProviderCanonicalName(): String = CobDetailedComplication::class.java.canonicalName!!
    override fun getComplicationAction(): ComplicationAction = ComplicationAction.WIZARD
}