package app.aaps.wear.interaction

import android.content.ComponentName
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.wear.watchface.editor.EditorSession
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.wear.R
import app.aaps.wear.preference.WearPreferenceActivity
import dagger.android.AndroidInjection
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

class ConfigurationActivity : WearPreferenceActivity() {

    @Inject lateinit var aapsLogger: AAPSLogger

    private var watchfaceComponentName: ComponentName? = null

    // Creation is kicked off unconditionally in onCreate() - NOT lazily on first "Complication N"
    // tap - because EditorSession's constructor internally calls registerForActivityResult(),
    // which throws IllegalStateException if called after the activity has moved past STARTED
    // (confirmed via a real stack trace when this was created on-demand from onPreferenceTreeClick,
    // i.e. long after the screen was already resumed). Kept alive and reused across multiple taps
    // (e.g. configuring slot 1 then slot 2 without reopening this screen); closed automatically by
    // its own lifecycle observer when this activity is destroyed - do not call .close() ourselves
    // (that causes a separate double-close crash, see requestComplicationPicker).
    private var editorSessionDeferred: Deferred<EditorSession>? = null
    private var complicationPickerInProgress = false

    companion object {

        /** Set by ComplicationPickerSupport when relaunching this activity (from
         *  WatchfaceConfigurationActivity's Settings-menu entry point) to open the picker for a
         *  specific CustomWatchface complication slot. Preferences are shown as usual either way -
         *  this only additionally triggers the picker for that one slot on entry. */
        const val EXTRA_COMPLICATION_SLOT_ID = "app.aaps.wear.interaction.EXTRA_COMPLICATION_SLOT_ID"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)

        // Extract the watchface component name from the intent BEFORE calling super.onCreate()
        // Wear OS 5.0 uses "COMPONENT_NAME_KEY" instead of the standard extras
        watchfaceComponentName = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("COMPONENT_NAME_KEY", ComponentName::class.java)
                ?: intent.getParcelableExtra(
                    "androidx.wear.watchface.editor.EXTRA_WATCH_FACE_COMPONENT",
                    ComponentName::class.java
                ) ?: intent.getParcelableExtra(
                    "android.support.wearable.watchface.extra.WATCH_FACE_COMPONENT",
                    ComponentName::class.java
                )
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("COMPONENT_NAME_KEY")
                ?: @Suppress("DEPRECATION") intent.getParcelableExtra(
                    "androidx.wear.watchface.editor.EXTRA_WATCH_FACE_COMPONENT"
                ) ?: @Suppress("DEPRECATION") intent.getParcelableExtra(
                    "android.support.wearable.watchface.extra.WATCH_FACE_COMPONENT"
                )
        }

        aapsLogger.debug(LTag.WEAR, "ConfigurationActivity::onCreate watchfaceComponentName = $watchfaceComponentName")

        super.onCreate(savedInstanceState)
        title = "Watchface"

        val view = window.decorView as ViewGroup
        removeBackgroundRecursively(view)
        view.background = ContextCompat.getDrawable(this, R.drawable.settings_background)
        view.requestFocus()

        // Add padding to the content view for spacing from top and bottom
        val contentView = findViewById<ViewGroup>(android.R.id.content)
        contentView?.setPadding(0, 50, 0, 50)

        // Start the EditorSession now (see the field comment above for why this can't be deferred
        // to the first "Complication N" tap). This is the sole activity in the app registered for
        // ACTION_WATCH_FACE_EDITOR, so both the long-press and Settings-menu entry points end up
        // here. If relaunched (by ComplicationPickerSupport) to open the picker for one specific
        // slot, do that in addition to showing preferences as usual (below), not instead of - the
        // user should land back on this same preferences screen afterward, able to configure
        // another slot without reopening.
        editorSessionDeferred = lifecycleScope.async { EditorSession.createOnWatchEditorSession(this@ConfigurationActivity) }

        val complicationSlotId = intent.getIntExtra(EXTRA_COMPLICATION_SLOT_ID, -1)
        if (complicationSlotId != -1) {
            requestComplicationPicker(complicationSlotId)
        }
    }

    /**
     * Opens the system's complication data source chooser for [slotId], reusing the single
     * [EditorSession] started in [onCreate] for as long as this activity is alive. Safe to call
     * repeatedly for different slots without reopening this screen.
     */
    fun requestComplicationPicker(slotId: Int) {
        if (complicationPickerInProgress) return
        complicationPickerInProgress = true
        lifecycleScope.launch {
            try {
                val session = editorSessionDeferred?.await() ?: return@launch
                session.openComplicationDataSourceChooser(slotId)
                // Don't call session.close() here: createOnWatchEditorSession() registers its own
                // lifecycle observer that closes the session automatically when this activity is
                // eventually destroyed. Closing it explicitly here too would cause that observer to
                // close an already-closed session on destroy, throwing
                // IllegalArgumentException("EditorSession method called after close()") and
                // crashing the app during activity teardown (confirmed via a real stack trace).
            } finally {
                complicationPickerInProgress = false
            }
        }
    }

    /**
     * Awaits the [EditorSession] started in [onCreate], so [ConfigurationFragment] can read each
     * slot's currently assigned complication data source from [EditorSession.complicationsDataSourceInfo].
     */
    suspend fun awaitEditorSession(): EditorSession = editorSessionDeferred!!.await()

    override fun createPreferenceFragment(): PreferenceFragmentCompat {
        val configFileName = intent.action

        // Determine which preference XML to load based on the watchface component
        val resXmlId = when (watchfaceComponentName?.className) {
            "app.aaps.wear.watchfaces.CustomWatchface" -> R.xml.watch_face_configuration_custom
            "app.aaps.wear.watchfaces.CircleWatchface" -> R.xml.watch_face_configuration_circle
            "app.aaps.wear.watchfaces.DigitalStyleWatchface" -> R.xml.watch_face_configuration_digitalstyle
            else -> {
                // Fallback: try to use the old method with action
                @Suppress("DiscouragedApi")
                resources.getIdentifier(configFileName, "xml", applicationContext.packageName)
            }
        }

        aapsLogger.debug(LTag.WEAR, "ConfigurationActivity::createPreferenceFragment --->> action: $configFileName")
        aapsLogger.debug(LTag.WEAR, "ConfigurationActivity::createPreferenceFragment --->> component: ${watchfaceComponentName?.className}")
        aapsLogger.debug(LTag.WEAR, "ConfigurationActivity::createPreferenceFragment --->> resXmlId: $resXmlId")

        return ConfigurationFragment.newInstance(resXmlId)
    }

    private fun removeBackgroundRecursively(parent: View) {
        if (parent is ViewGroup)
            for (i in 0 until parent.childCount)
                removeBackgroundRecursively(parent.getChildAt(i))
        parent.background = null
    }

    /**
     * Fragment for loading watchface configuration preferences
     */
    class ConfigurationFragment : PreferenceFragmentCompat() {

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            val resXmlId = arguments?.getInt(ARG_XML_RES_ID) ?: 0
            if (resXmlId != 0) {
                setPreferencesFromResource(resXmlId, rootKey)

                // Apply multiline layout to all preferences to prevent text truncation
                applyMultilineLayoutToAllPreferences(preferenceScreen)
            }
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            observeComplicationProviderSummaries()
        }

        /**
         * Shows each "Complication N" preference's currently assigned data source name as its
         * summary, e.g. "Heart Rate". [EditorSession.complicationsDataSourceInfo] is a StateFlow
         * that the library itself populates asynchronously once per slot right after the session
         * is created, and updates again for one slot after a successful pick via
         * [ConfigurationActivity.requestComplicationPicker] - collecting it here covers both the
         * initial display and the post-pick refresh with a single subscription, no separate
         * refresh call needed after the picker returns.
         */
        private fun observeComplicationProviderSummaries() {
            // Not CustomWatchface's preference screen (e.g. Circle/DigitalStyle) - nothing to do.
            if (!ComplicationPickerSupport.hasComplicationPreferences(this)) return

            viewLifecycleOwner.lifecycleScope.launch {
                val session = (requireActivity() as ConfigurationActivity).awaitEditorSession()
                session.complicationsDataSourceInfo.collect { infoBySlot ->
                    val namesBySlot = infoBySlot.mapValues { (_, info) -> info?.name }
                    ComplicationPickerSupport.applyComplicationSummaries(this@ConfigurationFragment, namesBySlot)
                    // This screen is the only place assignments can change, so it is also the only
                    // place that can keep the AAPS Settings menu's copy honest.
                    ComplicationPickerSupport.cacheAssignedDataSourceNames(requireContext(), namesBySlot)
                }
            }
        }

        // Already running as ConfigurationActivity (the only activity with a valid intent shape
        // for EditorSession), so handle the tap directly - no need to relaunch via
        // ComplicationPickerSupport the way WatchfaceConfigurationFragment does.
        override fun onPreferenceTreeClick(preference: Preference): Boolean {
            val slotId = ComplicationPickerSupport.slotIdFor(requireContext(), preference) ?: return super.onPreferenceTreeClick(preference)
            (requireActivity() as ConfigurationActivity).requestComplicationPicker(slotId)
            return true
        }

        /**
         * Recursively apply multiline layout to all preferences to allow long text to wrap
         * instead of being truncated with "..."
         */
        private fun applyMultilineLayoutToAllPreferences(group: androidx.preference.PreferenceGroup?) {
            group?.let {
                for (i in 0 until it.preferenceCount) {
                    val preference = it.getPreference(i)
                    // Apply the multiline layout
                    preference.layoutResource = R.layout.preference_material_multiline

                    // If this preference is a group (like PreferenceCategory), recurse into it
                    if (preference is PreferenceGroup) {
                        applyMultilineLayoutToAllPreferences(preference)
                    }
                }
            }
        }

        companion object {
            private const val ARG_XML_RES_ID = "xml_res_id"

            fun newInstance(xmlResId: Int): ConfigurationFragment {
                return ConfigurationFragment().apply {
                    arguments = Bundle().apply {
                        putInt(ARG_XML_RES_ID, xmlResId)
                    }
                }
            }
        }
    }
}