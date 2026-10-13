package app.aaps.wear.interaction

import android.content.ComponentName
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.wear.R
import app.aaps.wear.preference.WearPreferenceActivity
import dagger.android.AndroidInjection
import javax.inject.Inject

class ConfigurationActivity : WearPreferenceActivity() {

    @Inject lateinit var aapsLogger: AAPSLogger

    private var watchfaceComponentName: ComponentName? = null

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
    }

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

    override fun onPause() {
        super.onPause()
        finish()
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