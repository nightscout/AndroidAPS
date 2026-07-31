package app.aaps.wear.interaction

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.fragment.app.Fragment
import androidx.preference.Preference
import androidx.wear.watchface.editor.EditorRequest
import androidx.wear.watchface.editor.WatchFaceEditorContract
import app.aaps.wear.R
import app.aaps.wear.watchfaces.CustomWatchface

/**
 * Shared "Complication N" preference-tap handling for CustomWatchface's config screen
 * (watch_face_configuration_custom.xml), reused by both entry points that can show it: the
 * system's long-press "Customize" ([ConfigurationActivity]) and the AAPS Settings menu
 * ([WatchfaceConfigurationActivity]). Both launches resolve back to [ConfigurationActivity] -
 * the only activity in this app registered for ACTION_WATCH_FACE_EDITOR - which recognizes
 * [ConfigurationActivity.EXTRA_COMPLICATION_SLOT_ID] and opens the picker instead of showing
 * preferences again. No separate activity/intent-filter needed.
 */
internal class ComplicationPickerSupport(private val fragment: Fragment) {

    companion object {

        /** Maps a tapped preference to its complication slot ID, or null if it isn't one of the 3. */
        fun slotIdFor(context: Context, preference: Preference): Int? = when (preference.key) {
            context.getString(R.string.key_complication_1) -> CustomWatchface.COMPLICATION_SLOT_ID_1
            context.getString(R.string.key_complication_2) -> CustomWatchface.COMPLICATION_SLOT_ID_2
            context.getString(R.string.key_complication_3) -> CustomWatchface.COMPLICATION_SLOT_ID_3
            else                                            -> null
        }
    }

    private class ComplicationPickerContract(private val slotId: Int) : WatchFaceEditorContract() {

        override fun createIntent(context: Context, input: EditorRequest): Intent =
            super.createIntent(context, input).putExtra(ConfigurationActivity.EXTRA_COMPLICATION_SLOT_ID, slotId)
    }

    private val launchers: Map<Int, ActivityResultLauncher<EditorRequest>> = mapOf(
        CustomWatchface.COMPLICATION_SLOT_ID_1 to fragment.registerForActivityResult(ComplicationPickerContract(CustomWatchface.COMPLICATION_SLOT_ID_1)) { },
        CustomWatchface.COMPLICATION_SLOT_ID_2 to fragment.registerForActivityResult(ComplicationPickerContract(CustomWatchface.COMPLICATION_SLOT_ID_2)) { },
        CustomWatchface.COMPLICATION_SLOT_ID_3 to fragment.registerForActivityResult(ComplicationPickerContract(CustomWatchface.COMPLICATION_SLOT_ID_3)) { }
    )

    /** Returns true if [preference] was one of the 3 complication slots and the picker was launched. */
    fun handlePreferenceClick(preference: Preference): Boolean {
        val context = fragment.requireContext()
        val slotId = slotIdFor(context, preference) ?: return false
        // NOTE: this launch cannot currently reach the system's complication picker. Wear Services
        // only serves the provider chooser while SysUI's own WFEditingManagerImpl has registered an
        // editing session (it does that just before launching ACTION_WATCH_FACE_EDITOR itself, from
        // the long-press "Customize" flow). An app-initiated launch skips that registration, so the
        // chooser opens and is cancelled ~400ms later ("Cancelling ProviderChooserActivity ... there
        // is no editing session in progress"). Passing the real running instance id was verified NOT
        // to help - a request carrying the correct id was cancelled identically. Reaching the picker
        // from the AAPS menu therefore needs a different mechanism, not a richer EditorRequest.
        launchers.getValue(slotId).launch(
            EditorRequest(ComponentName(context, CustomWatchface::class.java), context.packageName, null)
        )
        return true
    }
}
