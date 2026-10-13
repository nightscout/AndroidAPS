package app.aaps.wear.complications

import android.app.PendingIntent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import app.aaps.core.interfaces.logging.LTag
import app.aaps.wear.R
import dagger.android.AndroidInjection

/**
 * COB Icon Complication
 *
 * Shows carbs on board (COB) with carbs icon
 * Tap action opens carb/wizard dialog
 *
 */
class CobIconComplication : ModernBaseComplicationProviderService() {

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
                    text = PlainComplicationText.Builder(text = statusData.cob).build(),
                    contentDescription = PlainComplicationText.Builder(text = "COB ${statusData.cob}").build()
                )
                    .setMonochromaticImage(MonochromaticImage.Builder(image = Icon.createWithResource(this, R.drawable.ic_carbs)).build())
                    .setTapAction(complicationPendingIntent)
                    .build()
            }

            else                             -> {
                aapsLogger.warn(LTag.WEAR, "Unexpected complication type $type")
                null
            }
        }
    }

    override fun getProviderCanonicalName(): String = CobIconComplication::class.java.canonicalName!!
    override fun getComplicationAction(): ComplicationAction = ComplicationAction.WIZARD
}