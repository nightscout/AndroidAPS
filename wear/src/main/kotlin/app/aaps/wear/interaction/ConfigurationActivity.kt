package app.aaps.wear.interaction

import android.content.ComponentName
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.wear.watchface.editor.EditorSession
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.wear.R
import app.aaps.wear.data.ComplicationDataRepository
import app.aaps.wear.preference.WearPreferenceActivity
import app.aaps.wear.watchfaces.CircleWatchface
import app.aaps.wear.watchfaces.CustomWatchface
import app.aaps.wear.watchfaces.DigitalStyleWatchface
import dagger.android.AndroidInjection
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

class ConfigurationActivity : WearPreferenceActivity(), CustomWatchfaceSettingsHost {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var complicationDataRepository: ComplicationDataRepository

    // Blocking: the preference screen is built synchronously in onCreate, and this is a local read.
    override fun storedWatchfaceConfiguration(): String? = runBlocking { complicationDataRepository.getCustomWatchface()?.json }

    private var watchfaceComponentName: ComponentName? = null

    // Created unconditionally in onCreate(), never lazily on the first "Complication N" tap:
    // EditorSession's constructor calls registerForActivityResult(), which throws
    // IllegalStateException once the activity has moved past STARTED. Kept alive and reused across
    // taps, and closed by its own lifecycle observer on destroy - never call .close() ourselves,
    // see requestComplicationPicker.
    private var editorSessionDeferred: Deferred<EditorSession>? = null
    private var complicationPickerInProgress = false

    companion object {

        /** Set by ComplicationPickerSupport when relaunching this activity to open the picker for one
         *  slot. Preferences are still shown as usual; this only adds the picker on entry. */
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

        // Start the EditorSession now - see the field comment above for why this cannot be deferred.
        // This is the only activity registered for ACTION_WATCH_FACE_EDITOR, so both the long-press
        // and Settings-menu entry points end up here. When relaunched for one specific slot, open the
        // picker in addition to showing preferences below, so the user lands back on this screen.
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
                // Never call session.close() here: createOnWatchEditorSession() registers a lifecycle
                // observer that closes it on destroy, and a second close throws
                // IllegalArgumentException("EditorSession method called after close()").
            } finally {
                complicationPickerInProgress = false
            }
        }
    }

    /**
     * Awaits the [EditorSession] started in [onCreate], so the fragment can read each slot's assigned
     * data source from [EditorSession.complicationsDataSourceInfo].
     */
    suspend fun awaitEditorSession(): EditorSession = editorSessionDeferred!!.await()

    override fun createPreferenceFragment(): PreferenceFragmentCompat {
        val configFileName = intent.action

        // CustomWatchface has its own screen, built in code, so it needs no xml here - see
        // CustomWatchfaceConfigurationFragment.
        if (watchfaceComponentName?.className == CustomWatchface::class.java.name) {
            aapsLogger.debug(LTag.WEAR, "ConfigurationActivity::createPreferenceFragment --->> CustomWatchface screen")
            return CustomWatchfaceConfigurationFragment.newInstance()
        }

        // Determine which preference XML to load based on the watchface component
        val resXmlId = when (watchfaceComponentName?.className) {
            CircleWatchface::class.java.name       -> R.xml.watch_face_configuration_circle
            DigitalStyleWatchface::class.java.name -> R.xml.watch_face_configuration_digitalstyle

            else                                   -> {
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
     * Fragment for loading watchface configuration preferences from a settings xml.
     *
     * Only Circle and DigitalStyle reach this - CustomWatchface has its own fragment - so nothing here
     * deals with complications.
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