package app.aaps.wear.complications

import android.app.PendingIntent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.CountUpTimeReference
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.TimeDifferenceComplicationText
import androidx.wear.watchface.complications.data.TimeDifferenceStyle
import app.aaps.core.interfaces.logging.LTag
import dagger.android.AndroidInjection
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * SGV Extended Complication 2
 *
 * Shows glucose data from AAPSClient2 (dataset 2)
 * Used in follower/caregiver mode to monitor a third patient
 *
 */
class SgvComplicationExt2 : ModernBaseComplicationProviderService() {

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
        val bgData2 = data.bgData2
        aapsLogger.debug(LTag.WEAR, "SgvComplicationExt2 building: dataset=2 sgv=${bgData2.sgvString} arrow=${bgData2.slopeArrow}")

        return when (type) {
            ComplicationType.SHORT_TEXT      -> {
                val shortText = bgData2.sgvString + bgData2.slopeArrow + "\uFE0E"

                val shortTitle = TimeDifferenceComplicationText.Builder(
                    style = TimeDifferenceStyle.STOPWATCH,
                    countUpTimeReference = CountUpTimeReference(Instant.ofEpochMilli(bgData2.timeStamp))
                )
                    .setMinimumTimeUnit(TimeUnit.MINUTES)
                    .build()

                ShortTextComplicationData.Builder(
                    text = PlainComplicationText.Builder(text = shortText).build(),
                    contentDescription = PlainComplicationText.Builder(text = "Glucose $shortText").build()
                )
                    .setTitle(shortTitle)
                    .setTapAction(complicationPendingIntent)
                    .build()
            }

            else                             -> {
                aapsLogger.warn(LTag.WEAR, "SgvComplicationExt2 unexpected type: $type")
                null
            }
        }
    }

    override fun getProviderCanonicalName(): String = SgvComplicationExt2::class.java.canonicalName!!
}