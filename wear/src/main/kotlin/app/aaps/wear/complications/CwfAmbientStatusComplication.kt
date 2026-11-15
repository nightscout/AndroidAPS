package app.aaps.wear.complications

import android.app.PendingIntent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import app.aaps.core.interfaces.logging.LTag
import app.aaps.wear.R
import app.aaps.wear.complications.cwf.CwfFaceComplication
import app.aaps.wear.data.ComplicationData as ComplicationStore

/**
 * Basal rate, carbs on board and insulin on board, for the ambient layer of the Watch Face Format
 * face.
 *
 * The same three values as [BrCobIobComplication], but written out in full. That one squeezes them
 * through `SmallestDoubleString.minimise()` with a character budget sized for a small round
 * complication, which is right there and wrong here: on a full width row it turned an insulin
 * reading of "1,19U" into ",19".
 *
 * The values arrive from the phone already formatted, units included, so this only has to place
 * them - see `EventData.Status`.
 */
class CwfAmbientStatusComplication : ModernBaseComplicationProviderService() {

    override fun buildComplicationData(
        type: ComplicationType,
        data: ComplicationStore,
        complicationPendingIntent: PendingIntent
    ): ComplicationData? {
        val status = data.statusData

        return when (type) {
            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData.Builder(
                    text = PlainComplicationText.Builder(
                        text = displayFormat.basalRateSymbol() + status.currentBasal
                    ).build(),
                    contentDescription = PlainComplicationText.Builder(
                        text = getString(R.string.complication_cwf_ambient_status)
                    ).build()
                )
                    .setTitle(
                        PlainComplicationText.Builder(
                            text = getString(R.string.complication_cwf_ambient_cob_iob, status.cob, status.iobSum)
                        ).build()
                    )
                    .setTapAction(complicationPendingIntent)
                    .build()

            else                        -> {
                aapsLogger.warn(LTag.WEAR, "Unexpected complication type $type")
                null
            }
        }
    }

    /**
     * Not tappable at all - same reason as [CwfAmbientBgComplication].
     *
     * The readout only appears in ambient, but the slot answers taps wherever it is drawn, so with a
     * tap action a tap on a sleeping watch opened AAPS instead of waking it. With no action the tap
     * falls through to the system and wakes the screen.
     */
    /**
     * Opens the AAPS menu while the watch is awake, and does nothing while it dozes - see
     * [readoutTapAction], which holds the rule and the two faults that shaped it.
     */
    override fun getComplicationAction(): ComplicationAction = readoutTapAction(CwfFaceComplication.isAmbient(this))

    override fun getProviderCanonicalName(): String = CwfAmbientStatusComplication::class.java.canonicalName!!
}
