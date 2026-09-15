package app.aaps.wear.interaction

import android.content.ComponentName
import android.content.Context
import android.content.Intent

/** Samsung SysUI's exported (protectionLevel="normal") watch face editor entry point. */
internal object SamsungWatchFaceEditor {

    private const val SYSUI_PACKAGE = "com.samsung.android.wearable.sysui"
    private const val ACTION_EDIT_WATCH_FACE = "com.samsung.android.wearable.sysui.action.EDIT_WATCH_FACE"

    /**
     * The only extra SysUI's receiver reads: the watch face service's flattened [ComponentName].
     * It bails out immediately (logging "no watchface") if this is absent, and again (logging
     * "not installed watchface") if the component doesn't resolve to a known watch face.
     */
    private const val EXTRA_WATCH_FACE = "watchface"

    private fun editIntent() = Intent(ACTION_EDIT_WATCH_FACE).setPackage(SYSUI_PACKAGE)

    /** True when this watch exposes Samsung's SysUI watch face editor receiver (any non-Samsung watch: false). */
    fun isAvailable(context: Context): Boolean =
        context.packageManager.queryBroadcastReceivers(editIntent(), 0).isNotEmpty()

    /**
     * Asks Samsung's SysUI to make [watchFace] the active watch face and open its editor.
     *
     * This is the only known way for the app to get the system to start a watch face *editing
     * session*, which Wear Services requires before it will serve the complication chooser (see
     * `ComplicationPickerSupport.handlePreferenceClick` for why nothing we can put in an intent
     * substitutes for it). SysUI then launches [ConfigurationActivity] itself, exactly as the
     * long-press "Customize" flow does, so from there everything - including the complication
     * chooser, for CustomWatchface - works normally. Applies to every watch face, not just
     * CustomWatchface: any of them benefits from actually being active while its settings are
     * open, complications or not.
     *
     * SysUI's handler is internally named "setActiveWatchfaceAndStartEditor" and does just
     * that: it adds the watch face to the user's favourites, **makes it active**, and only then
     * opens the editor. Activating the watch face is intended here, not a side effect to work
     * around - on Wear OS 5+ watches whose watch face picker no longer offers code-based faces,
     * this may be the only way to activate one of ours at all (unconfirmed - see the project
     * notes).
     *
     * Returns false when the receiver isn't present (any non-Samsung watch), so the caller can
     * degrade rather than assume the editor is on its way.
     */
    fun requestEditor(context: Context, watchFace: ComponentName): Boolean {
        if (!isAvailable(context)) return false
        context.sendBroadcast(editIntent().putExtra(EXTRA_WATCH_FACE, watchFace.flattenToString()))
        return true
    }
}