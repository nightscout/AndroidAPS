package app.aaps.wear.complications

import android.app.PendingIntent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import app.aaps.wear.R
import app.aaps.wear.complications.cwf.CwfFaceComplication
import app.aaps.wear.data.ComplicationData as ComplicationStore

/**
 * The glucose reading, for the ambient layer of the Watch Face Format face.
 *
 * Same content as [SgvComplication] - including its self-updating "minutes ago" text, which keeps
 * counting while our process is frozen in ambient - and differs only in where a tap goes.
 *
 * The reason it exists at all is that the ambient slots are **invisible while the watch is awake**:
 * they sit on top of the picture and only fade in when the watch dozes. A slot still answers taps
 * where it is drawn, so with [SgvComplication] in that slot, tapping the middle of an ordinary
 * Custom watch face opened the loop status screen for no visible reason. Sending it to the menu
 * instead makes every tap on the face behave the same way, whichever slot happens to be underneath.
 */
class CwfAmbientBgComplication : SgvComplication() {

    /**
     * Drops the age line while no reading has ever arrived.
     *
     * [SgvComplication] builds its title as a self-updating count-up from the reading's timestamp,
     * which is what keeps "minutes ago" honest while our process sleeps. With no reading at all the
     * timestamp is 0, so the count runs from 1970 and the watch shows "20699d" - seen on a fresh
     * install on a Wear OS 6 emulator, and what any new user would read before their first sync.
     *
     * Only the title is dropped; the value keeps whatever placeholder the face uses, so the row
     * still says something.
     */
    override fun buildComplicationData(
        type: ComplicationType,
        data: ComplicationStore,
        complicationPendingIntent: PendingIntent
    ): ComplicationData? =
        if (type == ComplicationType.SHORT_TEXT && data.bgData.timeStamp == 0L)
            ShortTextComplicationData.Builder(
                text = PlainComplicationText.Builder(text = data.bgData.sgvString).build(),
                contentDescription = PlainComplicationText.Builder(
                    text = getString(R.string.complication_cwf_ambient_bg)
                ).build()
            )
                .setTapAction(complicationPendingIntent)
                .build()
        else
            super.buildComplicationData(type, data, complicationPendingIntent)

    /**
     * Not tappable at all.
     *
     * These readouts sit on top of the picture and only appear in ambient, but a slot answers taps
     * wherever it is drawn. With a tap action, a tap on a sleeping watch was **consumed by the
     * complication** - it opened AAPS instead of waking the watch, which is what a wearer expects a
     * first tap to do. Reported twice on a Galaxy Watch 4. With no action the tap falls through to
     * the system and simply wakes the screen; the AAPS menu is still one tap away once awake.
     */
    /**
     * Opens the AAPS menu while the watch is awake, and does nothing while it dozes - see
     * [readoutTapAction], which holds the rule and the two faults that shaped it.
     */
    override fun getComplicationAction(): ComplicationAction = readoutTapAction(CwfFaceComplication.isAmbient(this))
    override fun getProviderCanonicalName(): String = CwfAmbientBgComplication::class.java.canonicalName!!
}
