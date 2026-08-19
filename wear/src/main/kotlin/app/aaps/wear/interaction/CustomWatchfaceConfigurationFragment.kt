package app.aaps.wear.interaction

import android.os.Bundle
import android.view.View
import androidx.annotation.StringRes
import androidx.lifecycle.lifecycleScope
import androidx.preference.CheckBoxPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import app.aaps.wear.R
import app.aaps.wear.watchfaces.CustomWatchface
import app.aaps.wear.watchfaces.utils.WatchFaceSettingRow
import kotlinx.coroutines.launch

/**
 * An activity that can show [CustomWatchfaceConfigurationFragment].
 *
 * It exists so the fragment can get the loaded CWF without reaching for the repository itself:
 * fragments are not Dagger-injected in this module, while both hosting activities already are.
 */
internal interface CustomWatchfaceSettingsHost {

    /** The CWF currently loaded, exactly as stored, or null if there is none. */
    fun storedWatchfaceConfiguration(): String?
}

/**
 * The Custom watch face's settings screen, built in code from what the watch face itself declares
 * (`CustomWatchface.settingRows`) instead of from a settings xml.
 *
 * Its own fragment, not the shared one, for two reasons. It is the only screen whose rows depend on
 * the loaded CWF - soon a row will be left out entirely when the current zip has no view that uses
 * it - and Digital's and Circle's screens must not be exposed to any of that. It is also the only
 * screen with complication rows, which is what lets the shared fragments stop caring about
 * complications at all.
 *
 * Hosted by both entry points, which differ in what they can do with a tap:
 * - [ConfigurationActivity] is the activity the system launches for the watch face editor, so it
 *   owns the live `EditorSession` and can open the data source picker directly. It is also the only
 *   place an assignment can change, so it is where the provider names are read and cached.
 * - [WatchfaceConfigurationActivity] (the AAPS Settings menu) has no session, so it can only show
 *   the cached names and relaunch through [ComplicationPickerSupport].
 */
class CustomWatchfaceConfigurationFragment : PreferenceFragmentCompat() {

    // Registration must happen unconditionally and before STARTED, so it cannot wait until the host
    // activity is known - see ComplicationPickerSupport. Unused when the host is
    // ConfigurationActivity, which opens the picker itself.
    private val complicationPickerSupport = ComplicationPickerSupport(this)

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        // A sub-screen shows what the watch face declares behind that key; the root screen shows the
        // rows relevant to the CWF currently loaded. The configuration is fetched here but never read
        // here: it is handed straight back to the watch face, the only thing allowed to interpret it.
        val subScreenKey = arguments?.getInt(ARG_SUB_SCREEN_KEY, 0) ?: 0
        val rows = if (subScreenKey != 0) {
            CustomWatchface.subScreenRows(subScreenKey)
        } else {
            CustomWatchface.settingRows((activity as? CustomWatchfaceSettingsHost)?.storedWatchfaceConfiguration())
        }
        buildScreen(rows)
    }

    /** Turns declared rows into androidx preferences. The only place that knows how a row is shown. */
    private fun buildScreen(rows: List<WatchFaceSettingRow>) {
        val context = preferenceManager.context
        val screen = preferenceManager.createPreferenceScreen(context)
        rows.forEach { row ->
            val preference = when (row) {
                is WatchFaceSettingRow.Toggle    -> CheckBoxPreference(context).apply {
                    setDefaultValue(row.defaultValue)
                    setSummaryOn(R.string.on)
                    setSummaryOff(R.string.off)
                }

                is WatchFaceSettingRow.Choice    -> ListPreference(context).apply {
                    setEntries(row.entries)
                    setEntryValues(row.entryValues)
                    setDefaultValue(row.defaultValue)
                    // Shows the chosen entry, the way "%s" does in a settings xml.
                    summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
                }

                // Navigation lives here rather than in the row: the watch face says "there is another
                // screen behind this key", and this fragment decides that a screen means an activity.
                is WatchFaceSettingRow.SubScreen -> Preference(context).apply {
                    intent = ComplicationTypeSettingsActivity.intent(context, row.key)
                }

                is WatchFaceSettingRow.Info      -> Preference(context).apply {
                    isSelectable = false
                }

                is WatchFaceSettingRow.Action    -> Preference(context)
            }
            preference.key = getString(row.key)
            preference.setTitle(row.title)
            // Long titles wrap instead of being cut, as on every other watch settings screen.
            preference.layoutResource = R.layout.preference_material_multiline
            preference.isPersistent = row is WatchFaceSettingRow.Toggle || row is WatchFaceSettingRow.Choice
            screen.addPreference(preference)
        }
        preferenceScreen = screen
        // Only now: setDependency() resolves the key through the PreferenceManager, which does not
        // know the screen until it has been set here - doing it while building each row throws
        // IllegalStateException("Dependency ... not found"). It also needs every row to exist
        // already, so a row could depend on one declared after it.
        rows.forEach { row ->
            val dependency = row.dependencyKey ?: return@forEach
            screen.findPreference<Preference>(getString(row.key))?.dependency = getString(dependency)
        }
    }

    /** True when showing a sub-screen, which carries no complication picker rows to summarise. */
    private val isSubScreen get() = (arguments?.getInt(ARG_SUB_SCREEN_KEY, 0) ?: 0) != 0

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (isSubScreen) return
        val host = activity as? ConfigurationActivity ?: return
        // A StateFlow the library fills in once per slot right after the session is created, and
        // again for one slot after a successful pick - so one subscription covers both the initial
        // display and the refresh, with no explicit call once the picker returns.
        viewLifecycleOwner.lifecycleScope.launch {
            host.awaitEditorSession().complicationsDataSourceInfo.collect { infoBySlot ->
                val namesBySlot = infoBySlot.mapValues { (_, info) -> info?.name }
                ComplicationPickerSupport.applyComplicationSummaries(this@CustomWatchfaceConfigurationFragment, namesBySlot)
                // This screen is the only place assignments can change, so it is also the only place
                // that can keep the AAPS Settings menu's copy honest.
                ComplicationPickerSupport.cacheAssignedDataSourceNames(requireContext(), namesBySlot)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Hosted by ConfigurationActivity the live session already fills these in. Anywhere else the
        // cache is all there is - in onResume rather than onViewCreated so it also refreshes on
        // return from the system editor, where the assignment may just have changed.
        if (isSubScreen || activity is ConfigurationActivity) return
        ComplicationPickerSupport.applyComplicationSummaries(this, ComplicationPickerSupport.cachedAssignedDataSourceNames(requireContext()))
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        val slotId = ComplicationPickerSupport.slotIdFor(requireContext(), preference) ?: return super.onPreferenceTreeClick(preference)
        // Already running in the activity that holds the editing session - open the picker directly
        // instead of relaunching that activity through ComplicationPickerSupport.
        (activity as? ConfigurationActivity)?.let {
            it.requestComplicationPicker(slotId)
            return true
        }
        return complicationPickerSupport.handlePreferenceClick(preference) || super.onPreferenceTreeClick(preference)
    }

    companion object {

        private const val ARG_SUB_SCREEN_KEY = "sub_screen_key"

        /** The root screen: the rows relevant to the CWF currently loaded. */
        fun newInstance() = CustomWatchfaceConfigurationFragment()

        /** The sub-screen the watch face declares behind [subScreenKey]. */
        fun newInstance(@StringRes subScreenKey: Int) = CustomWatchfaceConfigurationFragment().apply {
            arguments = Bundle().apply { putInt(ARG_SUB_SCREEN_KEY, subScreenKey) }
        }
    }
}
