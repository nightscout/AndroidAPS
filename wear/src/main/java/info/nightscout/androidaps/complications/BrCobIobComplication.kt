@file:Suppress("DEPRECATION")

package info.nightscout.androidaps.complications

import android.app.PendingIntent
import android.support.wearable.complications.ComplicationData
import android.support.wearable.complications.ComplicationText
import dagger.android.AndroidInjection
import info.nightscout.androidaps.data.RawDisplayData
import info.nightscout.androidaps.interaction.utils.DisplayFormat
import info.nightscout.androidaps.interaction.utils.SmallestDoubleString
import info.nightscout.rx.logging.LTag

import kotlin.math.max

/*
 * Created by dlvoy on 2019-11-12
 */
class BrCobIobComplication : BaseComplicationProviderService() {

    // Not derived from DaggerService, do injection here
    override fun onCreate() {
        AndroidInjection.inject(this)
        super.onCreate()
    }

    override fun buildComplicationData(dataType: Int, raw: RawDisplayData, complicationPendingIntent: PendingIntent): ComplicationData? {
        var complicationData: ComplicationData? = null
        if (dataType == ComplicationData.TYPE_SHORT_TEXT) {
            val cob = SmallestDoubleString(raw.status.cob, SmallestDoubleString.Units.USE).minimise(DisplayFormat.MIN_FIELD_LEN_COB)
            val iob = SmallestDoubleString(raw.status.iobSum, SmallestDoubleString.Units.USE).minimise(max(DisplayFormat.MIN_FIELD_LEN_IOB, DisplayFormat.MAX_FIELD_LEN_SHORT - 1 - cob.length))
            val builder = ComplicationData.Builder(ComplicationData.TYPE_SHORT_TEXT)
                .setShortText(ComplicationText.plainText(displayFormat.basalRateSymbol() + raw.status.currentBasal))
                .setShortTitle(ComplicationText.plainText("$cob $iob"))
                .setTapAction(complicationPendingIntent)
            complicationData = builder.build()
        } else {
            aapsLogger.warn(LTag.WEAR, "Unexpected complication type $dataType")
        }
        return complicationData
    }

    override fun getProviderCanonicalName(): String = BrCobIobComplication::class.java.canonicalName!!
}
