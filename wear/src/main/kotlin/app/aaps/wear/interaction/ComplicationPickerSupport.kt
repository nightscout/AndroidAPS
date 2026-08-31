package app.aaps.wear.interaction

import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.fragment.app.Fragment
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.wear.watchface.editor.EditorRequest
import androidx.wear.watchface.editor.WatchFaceEditorContract
import app.aaps.wear.R
import app.aaps.wear.watchfaces.utils.WatchFaceComplicationSlots

/**
 * Shared "Complication N" preference-tap handling, used by both screens that show those rows: the
 * long-press "Customize" flow ([ConfigurationActivity]) and the AAPS Settings menu
 * ([WatchfaceConfigurationActivity]). Both resolve back to [ConfigurationActivity] - the only
 * activity registered for ACTION_WATCH_FACE_EDITOR - which recognises
 * [ConfigurationActivity.EXTRA_COMPLICATION_SLOT_ID] and opens the picker.
 *
 * Names no watch face and decides nothing about how many slots exist: it asks the active watch face
 * through [WatchFaceComplicationSlots], reached via [WatchFaceCatalog], and offers one picker per
 * slot on the row that slot names.
 */
internal class ComplicationPickerSupport(private val fragment: Fragment) {

    companion object {

        private const val CACHED_PROVIDER_NAME_PREFIX = "complication_provider_name_"

        /** The watch face these screens configure, and the slots it hosts - see [WatchFaceCatalog]. */
        private val watchFace = WatchFaceCatalog.complicationWatchFace
        private val slots = WatchFaceCatalog.complicationSlotsFor(watchFace)
        private val slotIds = slots.map { it.id }

        /** Maps a tapped preference to its complication slot ID, or null if it isn't a slot row. */
        fun slotIdFor(context: Context, preference: Preference): Int? =
            slots.firstOrNull { context.getString(it.preferenceKey) == preference.key }?.id

        private fun keyForSlot(context: Context, slotId: Int): String? =
            slots.firstOrNull { it.id == slotId }?.let { context.getString(it.preferenceKey) }

        /**
         * Shows the assigned data source name under each "Complication N" entry.
         *
         * A slot missing from [namesBySlot] means "unknown" and is left with no summary rather than
         * being labelled unassigned; present with a `null` value means it genuinely has none.
         */
        fun applyComplicationSummaries(fragment: PreferenceFragmentCompat, namesBySlot: Map<Int, String?>) {
            val context = fragment.context ?: return
            for (slotId in slotIds) {
                val key = keyForSlot(context, slotId) ?: continue
                val preference = fragment.findPreference<Preference>(key) ?: continue
                if (!namesBySlot.containsKey(slotId)) continue
                preference.summary = namesBySlot[slotId] ?: context.getString(R.string.complication_summary_none)
            }
        }

        /**
         * Remembers what each slot is assigned to, so the AAPS Settings menu can show it later: that
         * screen has no editing session, and without one the system refuses the lookup - including
         * through `ComplicationDataSourceInfoRetriever`.
         *
         * Safe as a cache because [ConfigurationActivity] is the only route by which an assignment
         * can change, so this is written on the same path that observes every change.
         */
        fun cacheAssignedDataSourceNames(context: Context, namesBySlot: Map<Int, String?>) {
            val editor = PreferenceManager.getDefaultSharedPreferences(context).edit()
            for (slotId in slotIds) {
                if (!namesBySlot.containsKey(slotId)) continue
                // Empty string records a known-unassigned slot; no entry at all means never seen.
                editor.putString(CACHED_PROVIDER_NAME_PREFIX + slotId, namesBySlot[slotId] ?: "")
            }
            editor.apply()
        }

        /** Names cached by [cacheAssignedDataSourceNames]; slots never seen are absent from the map. */
        fun cachedAssignedDataSourceNames(context: Context): Map<Int, String?> {
            val preferences = PreferenceManager.getDefaultSharedPreferences(context)
            return slotIds.mapNotNull { slotId ->
                val cached = preferences.getString(CACHED_PROVIDER_NAME_PREFIX + slotId, null) ?: return@mapNotNull null
                slotId to cached.ifEmpty { null }
            }.toMap()
        }
    }

    private class ComplicationPickerContract(private val slotId: Int) : WatchFaceEditorContract() {

        override fun createIntent(context: Context, input: EditorRequest): Intent =
            super.createIntent(context, input).putExtra(ConfigurationActivity.EXTRA_COMPLICATION_SLOT_ID, slotId)
    }

    // One launcher per slot, since the contract carries the slot id. Registered at construction
    // because registerForActivityResult() must be called before the fragment reaches STARTED.
    private val launchers: Map<Int, ActivityResultLauncher<EditorRequest>> =
        slotIds.associateWith { slotId -> fragment.registerForActivityResult(ComplicationPickerContract(slotId)) { } }

    /**
     * Returns true if [preference] was a complication row and the picker was launched.
     *
     * **This launch cannot actually reach the system picker** and is kept only so the tap is not
     * silently inert: Wear Services serves the provider chooser only while an editing session is
     * registered, and an app-initiated launch has none, so the chooser opens and is cancelled after
     * ~400ms. Supplying the real watch-face instance id does not help - the session is system-side
     * state no intent extra can substitute for. Only reachable on watches where
     * `SamsungWatchFaceEditor.requestEditor` found no receiver; elsewhere the system editor has
     * already taken over and these rows are never tapped.
     */
    fun handlePreferenceClick(preference: Preference): Boolean {
        val context = fragment.requireContext()
        val slotId = slotIdFor(context, preference) ?: return false
        val watchFaceComponent = WatchFaceCatalog.componentNameFor(context, watchFace) ?: return false
        launchers.getValue(slotId).launch(EditorRequest(watchFaceComponent, context.packageName, null))
        return true
    }
}
