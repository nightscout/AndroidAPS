package app.aaps.wear.interaction

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceManager
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.wear.R
import app.aaps.wear.complications.BgGraphComplication
import app.aaps.wear.preference.WearPreferenceActivity
import app.aaps.wear.watchfaces.CircleWatchface
import app.aaps.wear.watchfaces.CustomWatchface
import app.aaps.wear.watchfaces.DigitalStyleWatchface
import javax.inject.Inject

class WatchfaceConfigurationActivity : WearPreferenceActivity(), SharedPreferences.OnSharedPreferenceChangeListener {

    @Inject lateinit var aapsLogger: AAPSLogger

    @Suppress("PrivatePropertyName")
    private val PHYSICAL_ACTIVITY = 1

    private var preferenceFile: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        // Inject dependencies first
        dagger.android.AndroidInjection.inject(this)

        // MUST set preferenceFile BEFORE calling super.onCreate() because super creates the fragment
        preferenceFile = intent.getIntExtra(getString(R.string.key_preference_id), R.xml.display_preferences)

        super.onCreate(savedInstanceState)

        // Opening one of the 3 watch faces' own settings (as opposed to the app-wide
        // display/graph/interface/tile/others screens, which aren't tied to any single watch face)
        // hands straight over to the system watch face editor. That both activates the watch face
        // being configured and shows this same preference screen backed by a live editing session
        // - the only state in which entries like CustomWatchface's "Complication N" actually work.
        // See [requestSystemWatchFaceEditor] for why activation is the point, not a side effect to
        // work around, and for what "hands over" concretely does.
        //
        // Finished immediately so this screen is never drawn: the editor takes ~1s to appear, and
        // without this the user watches one preference menu get replaced by a near-identical one.
        // Finishing before the first frame makes this a trampoline - Android skips drawing it - and
        // leaves back from the editor returning to the settings list rather than to a dead copy.
        //
        // Only when the broadcast was actually accepted. If there's no receiver (any non-Samsung
        // watch) we must keep this screen, since it's then the only one the user gets.
        if (savedInstanceState == null) {
            val watchFaceComponent = watchFaceComponentFor(preferenceFile)
            if (watchFaceComponent != null && requestSystemWatchFaceEditor(watchFaceComponent)) {
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

    override fun createPreferenceFragment(): PreferenceFragmentCompat {
        return WatchfaceConfigurationFragment.newInstance(preferenceFile)
    }

    private fun removeBackgroundRecursively(parent: View) {
        if (parent is ViewGroup)
            for (i in 0 until parent.childCount)
                removeBackgroundRecursively(parent.getChildAt(i))
        parent.background = null
    }

    /** The watch face [preferenceFile] configures, or null for the app-wide screens that aren't
     *  tied to any single watch face (display/graph/interface/tile/complication/others). */
    private fun watchFaceComponentFor(preferenceFile: Int): ComponentName? = when (preferenceFile) {
        R.xml.watch_face_configuration_custom       -> ComponentName(this, CustomWatchface::class.java)
        R.xml.watch_face_configuration_circle       -> ComponentName(this, CircleWatchface::class.java)
        R.xml.watch_face_configuration_digitalstyle -> ComponentName(this, DigitalStyleWatchface::class.java)
        else                                        -> ComponentName(this, CustomWatchface::class.java)
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
     * Fragment for loading watchface configuration preferences
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

        /** Samsung SysUI's exported (protectionLevel="normal") watch face editor entry point. */
        private const val SYSUI_PACKAGE = "com.samsung.android.wearable.sysui"
        private const val ACTION_EDIT_WATCH_FACE = "com.samsung.android.wearable.sysui.action.EDIT_WATCH_FACE"

        /**
         * The only extra SysUI's receiver reads: the watch face service's flattened [ComponentName].
         * It bails out immediately (logging "no watchface") if this is absent, and again (logging
         * "not installed watchface") if the component doesn't resolve to a known watch face.
         */
        private const val EXTRA_WATCH_FACE = "watchface"

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
        private fun Context.requestSystemWatchFaceEditor(watchFace: ComponentName): Boolean {
            val intent = Intent(ACTION_EDIT_WATCH_FACE).setPackage(SYSUI_PACKAGE)
            if (packageManager.queryBroadcastReceivers(intent, 0).isEmpty()) return false
            sendBroadcast(intent.putExtra(EXTRA_WATCH_FACE, watchFace.flattenToString()))
            return true
        }
    }
}
