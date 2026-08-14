package app.aaps.wear.interaction

import android.content.ComponentName
import android.content.Context
import app.aaps.wear.R
import app.aaps.wear.watchfaces.CircleWatchface
import app.aaps.wear.watchfaces.CustomWatchface
import app.aaps.wear.watchfaces.DigitalStyleWatchface
import app.aaps.wear.watchfaces.utils.ComplicationSlotInfo
import app.aaps.wear.watchfaces.utils.WatchfaceViewAdapter.Companion.SelectedWatchFace

/**
 * The single source of truth for what identifies each of the 3 built-in, code-based watch faces:
 * its [ComponentName] and its own preference screen. [SelectedWatchFace.NONE] maps to nothing -
 * there is no watch face to activate or configure for it.
 */
internal object WatchFaceCatalog {

    fun componentNameFor(context: Context, watchFace: SelectedWatchFace): ComponentName? = when (watchFace) {
        SelectedWatchFace.CUSTOM  -> ComponentName(context, CustomWatchface::class.java)
        SelectedWatchFace.DIGITAL -> ComponentName(context, DigitalStyleWatchface::class.java)
        SelectedWatchFace.CIRCLE  -> ComponentName(context, CircleWatchface::class.java)
        SelectedWatchFace.NONE    -> null
    }

    fun preferenceXmlFor(watchFace: SelectedWatchFace): Int? = when (watchFace) {
        SelectedWatchFace.CUSTOM  -> R.xml.watch_face_configuration_custom
        SelectedWatchFace.DIGITAL -> R.xml.watch_face_configuration_digitalstyle
        SelectedWatchFace.CIRCLE  -> R.xml.watch_face_configuration_circle
        SelectedWatchFace.NONE    -> null
    }

    /**
     * The complication slots [watchFace] hosts - an empty list when it hosts none, which is the case
     * for every watch face but the custom one today.
     *
     * Keeping this here rather than in [ComplicationPickerSupport] is what lets that class stay
     * watch-face-agnostic: this object is already the single place allowed to name a watch face.
     */
    fun complicationSlotsFor(watchFace: SelectedWatchFace): List<ComplicationSlotInfo> = when (watchFace) {
        SelectedWatchFace.CUSTOM  -> CustomWatchface.complicationSlots
        SelectedWatchFace.DIGITAL -> emptyList()
        SelectedWatchFace.CIRCLE  -> emptyList()
        SelectedWatchFace.NONE    -> emptyList()
    }

    /**
     * The watch face whose settings screen the complication rows belong to.
     *
     * A constant while exactly one watch face hosts complications. When a second one does, this
     * becomes a per-screen lookup - both settings activities already resolve which watch face they
     * are showing (from the editor intent's component, and from `key_selected_watchface`), so the
     * value is available where it will be needed; only this line has to change.
     */
    val complicationWatchFace: SelectedWatchFace = SelectedWatchFace.CUSTOM
}