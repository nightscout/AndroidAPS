package app.aaps.wear.interaction

import android.Manifest
import android.content.ComponentName
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceManager
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.wear.R
import app.aaps.wear.complications.BgGraphComplication
import app.aaps.wear.data.ComplicationDataRepository
import app.aaps.wear.preference.WearPreferenceActivity
import app.aaps.wear.watchfaces.utils.WatchfaceViewAdapter.Companion.SelectedWatchFace
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

class WatchfaceConfigurationActivity : WearPreferenceActivity(), SharedPreferences.OnSharedPreferenceChangeListener, CustomWatchfaceSettingsHost {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var complicationDataRepository: ComplicationDataRepository

    // See the same override in ConfigurationActivity for why this blocks.
    override fun storedWatchfaceConfiguration(): String? = runBlocking { complicationDataRepository.getCustomWatchface()?.json }

    @Suppress("PrivatePropertyName")
    private val PHYSICAL_ACTIVITY = 1

    private var preferenceFile: Int = 0

    // Both set before super.onCreate(), which is what creates the fragment - see createPreferenceFragment().
    private var showsCustomWatchface = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // Inject dependencies first
        dagger.android.AndroidInjection.inject(this)

        // MUST set preferenceFile BEFORE calling super.onCreate() because super creates the fragment
        val requestedWatchFace = intent.getIntExtra(getString(R.string.key_selected_watchface), -1)
            .takeIf { it >= 0 }
            ?.let { SelectedWatchFace.fromId(it) }

        showsCustomWatchface = requestedWatchFace == SelectedWatchFace.CUSTOM

        preferenceFile = requestedWatchFace
            ?.let { WatchFaceCatalog.preferenceXmlFor(it) }
            ?: intent.getIntExtra(getString(R.string.key_preference_id), R.xml.display_preferences)

        super.onCreate(savedInstanceState)

        // Only the 3 dedicated watch-face menu entries pass key_selected_watchface. The app-wide
        // display/graph/interface/complication/others screens (and the phone-triggered
        // OpenSettings default) must never activate a watch face as a side effect of being
        // opened, so the SysUI hand-off below only ever runs when one was explicitly requested.
        // Activating the watch face is the point, not a side effect to work around: it is what
        // makes the preference screen show a live editing session - see [SamsungWatchFaceEditor].
        //
        // Finished immediately so this screen is never drawn: the editor takes ~1s to appear, and
        // without this the user watches one preference menu get replaced by a near-identical one.
        // Finishing before the first frame makes this a trampoline - Android skips drawing it - and
        // leaves back from the editor returning to the settings list rather than to a dead copy.
        //
        // Only when the broadcast was actually accepted. If there's no receiver (any non-Samsung
        // watch) we must keep this screen, since it's then the only one the user gets.
        if (savedInstanceState == null && requestedWatchFace != null) {
            val watchFaceComponent = WatchFaceCatalog.componentNameFor(this, requestedWatchFace)
            if (watchFaceComponent != null && SamsungWatchFaceEditor.requestEditor(this, watchFaceComponent)) {
                finish()
                return
            }
        }

        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this)

        val view = window.decorView as ViewGroup
        removeBackgroundRecursively(view)
        view.background = ContextCompat.getDrawable(this, R.drawable.settings_background)
        view.requestFocus()

        // Add padding to the content view for spacing from top and bottom
        val contentView = findViewById<ViewGroup>(android.R.id.content)
        contentView?.setPadding(0, 50, 0, 50)
    }

    // CustomWatchface's screen is built in code by the watch face's own fragment, so it takes no xml
    // - see CustomWatchfaceConfigurationFragment. Every other screen this activity shows (the other
    // two watch faces, and the app-wide display/graph/interface/others screens) still comes from one.
    override fun createPreferenceFragment(): PreferenceFragmentCompat =
        if (showsCustomWatchface) CustomWatchfaceConfigurationFragment.newInstance()
        else WatchfaceConfigurationFragment.newInstance(preferenceFile)

    private fun removeBackgroundRecursively(parent: View) {
        if (parent is ViewGroup)
            for (i in 0 until parent.childCount)
                removeBackgroundRecursively(parent.getChildAt(i))
        parent.background = null
    }

    override fun onSharedPreferenceChanged(sp: SharedPreferences, key: String?) {
        if (key == getString(R.string.key_complication_bg_graph_hours)) {
            // Re-render the graph complication immediately instead of waiting for the next BG/poll
            ComplicationDataSourceUpdateRequester
                .create(this, ComponentName(this, BgGraphComplication::class.java))
                .requestUpdateAll()
        }
        if (key == getString(R.string.key_heart_rate_sampling) && sp.getBoolean(key, false))
            requestBodySensorPermission()
        if (key == getString(R.string.key_steps_sampling)) {
            if (sp.getBoolean(key, false)) {
                // Check if the permission is already granted, if not, request it
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_DENIED) {
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACTIVITY_RECOGNITION), PHYSICAL_ACTIVITY)
                }
            }
        }
    }

    private fun requestBodySensorPermission() {
        val permission = Manifest.permission.BODY_SENSORS
        if (ActivityCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(permission), BODY_SENSOR_PERMISSION_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode == BODY_SENSOR_PERMISSION_REQUEST_CODE) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                aapsLogger.info(LTag.WEAR, "Sensor permission for heart rate granted")
            } else {
                aapsLogger.warn(LTag.WEAR, "Sensor permission for heart rate denied")
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    /**
     * Fragment for loading preferences from a settings xml.
     *
     * Nothing here deals with complications: the only screen that has any is CustomWatchface's, and
     * that one is built by its own fragment.
     */
    class WatchfaceConfigurationFragment : PreferenceFragmentCompat() {

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

            fun newInstance(xmlResId: Int): WatchfaceConfigurationFragment {
                return WatchfaceConfigurationFragment().apply {
                    arguments = Bundle().apply {
                        putInt(ARG_XML_RES_ID, xmlResId)
                    }
                }
            }
        }
    }

    companion object {
        private const val BODY_SENSOR_PERMISSION_REQUEST_CODE = 1
    }
}
