package app.aaps.wear.interaction

import android.Manifest
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
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.wear.R
import app.aaps.wear.preference.WearPreferenceActivity
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

    override fun onSharedPreferenceChanged(sp: SharedPreferences, key: String?) {
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
    }
}
