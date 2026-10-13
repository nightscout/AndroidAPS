package app.aaps.wear.complications

import android.app.PendingIntent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import app.aaps.core.interfaces.logging.LTag
import dagger.android.AndroidInjection

/**
 * Basal Rate (BR) Complication
 *
 * Shows current basal rate with basal symbol
 *
 */
class BrComplication : ModernBaseComplicationProviderService() {

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
                ShortTextComplicationData.Builder(
                    text = PlainComplicationText.Builder(text = displayFormat.basalRateSymbol() + statusData.currentBasal).build(),
                    contentDescription = PlainComplicationText.Builder(text = "Basal Rate").build()
                )
                    .setTapAction(complicationPendingIntent)
                    .build()
            }

            else                             -> {
                aapsLogger.warn(LTag.WEAR, "Unexpected complication type $type")
                null
            }
        }
    }

    override fun getProviderCanonicalName(): String = BrComplication::class.java.canonicalName!!
}