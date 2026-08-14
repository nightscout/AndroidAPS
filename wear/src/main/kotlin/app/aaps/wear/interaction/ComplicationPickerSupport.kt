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
 * Shared "Complication N" preference-tap handling for the config screen of whichever watch face
 * declares complication rows, reused by both entry points that can show it: the system's long-press
 * "Customize" ([ConfigurationActivity]) and the AAPS Settings menu
 * ([WatchfaceConfigurationActivity]). Both launches resolve back to [ConfigurationActivity] -
 * the only activity in this app registered for ACTION_WATCH_FACE_EDITOR - which recognizes
 * [ConfigurationActivity.EXTRA_COMPLICATION_SLOT_ID] and opens the picker instead of showing
 * preferences again. No separate activity/intent-filter needed.
 *
 * Getting from the AAPS Settings menu to that live session at all is handled one level up, in
 * [WatchfaceConfigurationActivity] via [SamsungWatchFaceEditor] - it is not specific to
 * complications (it is what makes any watch face's settings screen show a real editing session),
 * so it does not live here.
 *
 * Nothing here names a watch face, and nothing here decides how many complications a watch face has
 * or which of them the user wants: it asks the active one for the slots it hosts
 * ([WatchFaceComplicationSlots], reached through [WatchFaceCatalog]) and offers one picker per slot,
 * on the settings row each slot names. A watch face with a fixed layout answers with a constant
 * list, one built from a loaded template with a derived one - this class cannot tell the difference
 * and does not need to.
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

        /** True if [fragment] is showing the screen that carries these complication rows. */
        fun hasComplicationPreferences(fragment: PreferenceFragmentCompat): Boolean {
            val context = fragment.context ?: return false
            return slots.any { fragment.findPreference<Preference>(context.getString(it.preferenceKey)) != null }
        }

        /**
         * Shows the assigned data source name under each "Complication N" entry.
         *
         * A slot **missing** from [namesBySlot] means "we don't know", and is deliberately left
         * with no summary at all rather than being labelled as unassigned - claiming a slot is
         * empty when it isn't is worse than saying nothing. A slot present with a `null` value
         * means we do know, and it genuinely has no data source.
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
         * Remembers what each slot is assigned to, so the AAPS Settings menu can show it later.
         *
         * That screen cannot read the assignments itself: it has no editing session, and without
         * one the system refuses the lookup (`ComplicationsManager failed to fetch
         * ComplicationProviderInfos` - the same refusal behind the picker limitation documented on
         * [handlePreferenceClick]). [ComplicationDataSourceInfoRetriever] looked like a way around
         * that and is not - it was tried on device and refused identically.
         *
         * Caching is sound here because [ConfigurationActivity] is the only route by which an
         * assignment can change, so this is written on the same path that observes every change
         * rather than being a snapshot that can silently drift.
         */
        fun cacheAssignedDataSourceNames(context: Context, namesBySlot: Map<Int, String?>) {
            val editor = PreferenceManager.getDefaultSharedPreferences(context).edit()
            for (slotId in slotIds) {
                if (!namesBySlot.containsKey(slotId)) continue
                // Empty string records a known-unassigned slot, which is different from having no
                // entry at all (never seen it) - see [applyComplicationSummaries].
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

    // One launcher per slot, since the contract carries the slot id. Registered here, at construction
    // time, because registerForActivityResult() must be called before the fragment reaches STARTED.
    private val launchers: Map<Int, ActivityResultLauncher<EditorRequest>> =
        slotIds.associateWith { slotId -> fragment.registerForActivityResult(ComplicationPickerContract(slotId)) { } }

    /**
     * Returns true if [preference] was one of the complication slots and the picker was launched.
     *
     * Only reached on watches where [SamsungWatchFaceEditor.requestEditor] found no receiver,
     * because where it does, [WatchfaceConfigurationActivity] has already handed over to the system
     * editor and this screen's entries are never tapped.
     *
     * The launch below cannot actually reach the system's complication picker: Wear Services only
     * serves the provider chooser while an editing session is registered, and
     * `ComplicationHelperActivity`'s own documentation states that from Android R "this API can
     * only be called during an editing session". An app-initiated launch has no session, so the
     * chooser opens and is cancelled ~400ms later ("Cancelling ProviderChooserActivity ... there is
     * no editing session in progress"). Passing the real running instance id was verified NOT to
     * help - a request carrying the correct id was cancelled identically - because the session is
     * system-side state that no intent extra can stand in for. Kept only so the tap isn't silently
     * inert on those watches.
     */
    fun handlePreferenceClick(preference: Preference): Boolean {
        val context = fragment.requireContext()
        val slotId = slotIdFor(context, preference) ?: return false
        val watchFaceComponent = WatchFaceCatalog.componentNameFor(context, watchFace) ?: return false
        launchers.getValue(slotId).launch(EditorRequest(watchFaceComponent, context.packageName, null))
        return true
    }
}
