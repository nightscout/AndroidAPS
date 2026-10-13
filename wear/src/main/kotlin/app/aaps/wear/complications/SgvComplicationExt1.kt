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
 * SGV Extended Complication 1
 *
 * Shows glucose data from AAPSClient1 (dataset 1)
 * Used in follower/caregiver mode to monitor a second patient
 *
 */
class SgvComplicationExt1 : ModernBaseComplicationProviderService() {

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
        // Use dataset 1 (AAPSClient1)
        val bgData1 = data.bgData1
        aapsLogger.debug(LTag.WEAR, "SgvComplicationExt1 building: dataset=1 sgv=${bgData1.sgvString} arrow=${bgData1.slopeArrow}")

        return when (type) {
            ComplicationType.SHORT_TEXT      -> {
                val shortText = bgData1.sgvString + bgData1.slopeArrow + "\uFE0E"

                val shortTitle = TimeDifferenceComplicationText.Builder(
                    style = TimeDifferenceStyle.STOPWATCH,
                    countUpTimeReference = CountUpTimeReference(Instant.ofEpochMilli(bgData1.timeStamp))
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
                aapsLogger.warn(LTag.WEAR, "SgvComplicationExt1 unexpected type: $type")
                null
            }
        }
    }

    override fun getProviderCanonicalName(): String = SgvComplicationExt1::class.java.canonicalName!!
}