@file:Suppress("DEPRECATION")

package info.nightscout.androidaps.complications

import android.app.PendingIntent
import android.support.wearable.complications.ComplicationData
import android.support.wearable.complications.ComplicationText
import dagger.android.AndroidInjection
import info.nightscout.androidaps.data.RawDisplayData
import info.nightscout.rx.logging.LTag


/*
 * Created by dlvoy on 2019-11-12
 */
class SgvComplication : BaseComplicationProviderService() {

    // Not derived from DaggerService, do injection here
    override fun onCreate() {
        AndroidInjection.inject(this)
        super.onCreate()
    }

    override fun buildComplicationData(dataType: Int, raw: RawDisplayData, complicationPendingIntent: PendingIntent): ComplicationData? {
        var complicationData: ComplicationData? = null
        when (dataType) {
            ComplicationData.TYPE_SHORT_TEXT -> {
                val builder = ComplicationData.Builder(ComplicationData.TYPE_SHORT_TEXT)
                    .setShortText(ComplicationText.plainText(raw.singleBg.sgvString + raw.singleBg.slopeArrow + "\uFE0E"))
                    .setShortTitle(ComplicationText.plainText(displayFormat.shortTrend(raw)))
                    .setTapAction(complicationPendingIntent)
                complicationData = builder.build()
            }

            else                             -> aapsLogger.warn(LTag.WEAR, "Unexpected complication type $dataType")
        }
        return complicationData
    }

    override fun getProviderCanonicalName(): String = SgvComplication::class.java.canonicalName!!
    override fun usesSinceField(): Boolean = true
}