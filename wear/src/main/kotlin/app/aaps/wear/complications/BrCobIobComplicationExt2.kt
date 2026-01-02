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
import kotlin.math.max

/**
 * Basal Rate + COB + IOB Extended Complication 2
 *
 * Shows basal rate, carbs on board (COB), and insulin on board (IOB) from AAPSClient2 (dataset 2)
 * Used in follower/caregiver mode to monitor a third patient
 * Text: Basal rate with symbol
 * Title: COB and IOB values (both minimized to fit)
 *
 */
class BrCobIobComplicationExt2 : ModernBaseComplicationProviderService() {

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
        // Use dataset 2 (AAPSClient2)
        val statusData2 = data.statusData2

        return when (type) {
            ComplicationType.SHORT_TEXT      -> {
                val cob = SmallestDoubleString(statusData2.cob, SmallestDoubleString.Units.USE).minimise(DisplayFormat.MIN_FIELD_LEN_COB)
                val iob = SmallestDoubleString(statusData2.iobSum, SmallestDoubleString.Units.USE).minimise(max(DisplayFormat.MIN_FIELD_LEN_IOB, DisplayFormat.MAX_FIELD_LEN_SHORT - 1 - cob.length))

                ShortTextComplicationData.Builder(
                    text = PlainComplicationText.Builder(text = displayFormat.basalRateSymbol() + statusData2.currentBasal).build(),
                    contentDescription = PlainComplicationText.Builder(text = "Basal Rate, COB, IOB").build()
                )
                    .setTitle(PlainComplicationText.Builder(text = "$cob $iob").build())
                    .setTapAction(complicationPendingIntent)
                    .build()
            }

            else                             -> {
                aapsLogger.warn(LTag.WEAR, "Unexpected complication type $type")
                null
            }
        }
    }

    override fun getProviderCanonicalName(): String = BrCobIobComplicationExt2::class.java.canonicalName!!
}
