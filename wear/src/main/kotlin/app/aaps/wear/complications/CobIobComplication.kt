package app.aaps.wear.complications

import android.app.PendingIntent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import app.aaps.core.interfaces.logging.LTag
import app.aaps.wear.interaction.utils.DisplayFormat
import app.aaps.wear.interaction.utils.SmallestDoubleString
import dagger.android.AndroidInjection

/**
 * COB+IOB Complication
 *
 * Shows both carbs on board (COB) and insulin on board (IOB)
 * Text: COB value
 * Title: IOB value (minimized to fit)
 *
 */
class CobIobComplication : ModernBaseComplicationProviderService() {

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
            ComplicationType.SHORT_TEXT      -> {
                val cob = statusData.cob
                val iob = SmallestDoubleString(statusData.iobSum, SmallestDoubleString.Units.USE).minimise(DisplayFormat.MAX_FIELD_LEN_SHORT)

                ShortTextComplicationData.Builder(
                    text = PlainComplicationText.Builder(text = cob).build(),
                    contentDescription = PlainComplicationText.Builder(text = "COB $cob IOB $iob").build()
                )
                    .setTitle(PlainComplicationText.Builder(text = iob).build())
                    .setTapAction(complicationPendingIntent)
                    .build()
            }

            else                             -> {
                aapsLogger.warn(LTag.WEAR, "Unexpected complication type $type")
                null
            }
        }
    }

    override fun getProviderCanonicalName(): String = CobIobComplication::class.java.canonicalName!!
}
