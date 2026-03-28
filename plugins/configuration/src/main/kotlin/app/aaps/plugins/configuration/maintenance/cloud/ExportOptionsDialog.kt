package app.aaps.plugins.configuration.maintenance.cloud

import android.content.Context
import android.widget.CheckBox
import android.widget.Switch
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.plugins.configuration.R
import app.aaps.plugins.configuration.activities.DaggerAppCompatActivityWithResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExportOptionsDialog @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val rh: ResourceHelper,
    private val sp: SP,
    private val cloudStorageManager: CloudStorageManager
) {
    
    companion object {
        private const val LOG_PREFIX = CloudConstants.LOG_PREFIX
        
        // SharedPreferences keys for export destination settings
        const val PREF_ALL_CLOUD_ENABLED = "export_all_cloud_enabled"
        const val PREF_LOG_EMAIL_ENABLED = "export_log_email_enabled"
        const val PREF_LOG_CLOUD_ENABLED = "export_log_cloud_enabled"
        const val PREF_SETTINGS_LOCAL_ENABLED = "export_settings_local_enabled"
        const val PREF_SETTINGS_CLOUD_ENABLED = "export_settings_cloud_enabled"
        const val PREF_CSV_LOCAL_ENABLED = "export_csv_local_enabled"
        const val PREF_CSV_CLOUD_ENABLED = "export_csv_cloud_enabled"
    }
    
    /**
     * Show export options configuration dialog
     */
    fun showExportOptionsDialog(
        activity: DaggerAppCompatActivityWithResult,
        onSettingsChanged: () -> Unit = {}
    ) {
        val dialogView = activity.layoutInflater.inflate(R.layout.dialog_export_options, null)
        
        val allCloudSwitch = dialogView.findViewById<Switch>(R.id.all_cloud_switch)
        val logEmailSwitch = dialogView.findViewById<Switch>(R.id.log_email_switch)
        val logCloudSwitch = dialogView.findViewById<Switch>(R.id.log_cloud_switch)
        val settingsLocalCheckbox = dialogView.findViewById<CheckBox>(R.id.settings_local_checkbox)
        val settingsCloudCheckbox = dialogView.findViewById<CheckBox>(R.id.settings_cloud_checkbox)
        val csvLocalSwitch = dialogView.findViewById<Switch>(R.id.csv_local_switch)
        val csvCloudSwitch = dialogView.findViewById<Switch>(R.id.csv_cloud_switch)
        
        // Load current settings
        allCloudSwitch.isChecked = sp.getBoolean(PREF_ALL_CLOUD_ENABLED, false)
        logEmailSwitch.isChecked = sp.getBoolean(PREF_LOG_EMAIL_ENABLED, true) // Default to email
        logCloudSwitch.isChecked = sp.getBoolean(PREF_LOG_CLOUD_ENABLED, false)
        settingsLocalCheckbox.isChecked = sp.getBoolean(PREF_SETTINGS_LOCAL_ENABLED, true) // Default to local
        settingsCloudCheckbox.isChecked = sp.getBoolean(PREF_SETTINGS_CLOUD_ENABLED, false)
        csvLocalSwitch.isChecked = sp.getBoolean(PREF_CSV_LOCAL_ENABLED, true) // Default to local
        csvCloudSwitch.isChecked = sp.getBoolean(PREF_CSV_CLOUD_ENABLED, false)
        
        // Check if cloud directory is configured
        val isCloudConfigured = cloudStorageManager.isCloudStorageActive()
        
        // Disable cloud options if not configured
        if (!isCloudConfigured) {
            logCloudSwitch.isEnabled = false
            settingsCloudCheckbox.isEnabled = false
            csvCloudSwitch.isEnabled = false
            
            // Force disable cloud options and enable local/email options
            logCloudSwitch.isChecked = false
            settingsCloudCheckbox.isChecked = false
            csvCloudSwitch.isChecked = false
            
            if (!logEmailSwitch.isChecked && !logCloudSwitch.isChecked) {
                logEmailSwitch.isChecked = true
            }
            if (!settingsLocalCheckbox.isChecked && !settingsCloudCheckbox.isChecked) {
                settingsLocalCheckbox.isChecked = true
            }
            if (!csvLocalSwitch.isChecked && !csvCloudSwitch.isChecked) {
                csvLocalSwitch.isChecked = true
            }
        }
        
        // Set up mutual exclusivity for log and csv rows (they still use switches)
        setupMutualExclusivity(logEmailSwitch, logCloudSwitch)
        setupMutualExclusivity(csvLocalSwitch, csvCloudSwitch)
        
        // Set up at-least-one-selected logic for settings checkboxes
        setupAtLeastOneSelected(settingsLocalCheckbox, settingsCloudCheckbox)
        
        // Apply master All-Cloud behavior - shared logic for both initialization and user interaction
        val applyAllCloudState: (Boolean) -> Unit = { enabled ->
            if (enabled) {
                // Require cloud for all rows, but preserve local settings checkbox state
                logCloudSwitch.isChecked = true
                settingsCloudCheckbox.isChecked = true
                csvCloudSwitch.isChecked = true
                logEmailSwitch.isChecked = false
                // settingsLocalCheckbox - keep current state, don't force uncheck
                csvLocalSwitch.isChecked = false

                // Disable per-row toggles when master is on (except settings local which can be toggled)
                logEmailSwitch.isEnabled = false
                logCloudSwitch.isEnabled = false
                // settingsLocalCheckbox stays enabled - user can choose to have both local+cloud
                settingsCloudCheckbox.isEnabled = false
                csvLocalSwitch.isEnabled = false
                csvCloudSwitch.isEnabled = false
            } else {
                // Re-enable per-row toggles (cloud options depend on configuration)
                // Don't reset values - just allow user to change them
                logEmailSwitch.isEnabled = true
                logCloudSwitch.isEnabled = isCloudConfigured
                settingsLocalCheckbox.isEnabled = true
                settingsCloudCheckbox.isEnabled = isCloudConfigured
                csvLocalSwitch.isEnabled = true
                csvCloudSwitch.isEnabled = isCloudConfigured
            }
        }
        
        // Apply initial state based on saved preferences
        if (allCloudSwitch.isChecked && isCloudConfigured) {
            applyAllCloudState(true)
        }

        allCloudSwitch.setOnCheckedChangeListener { _, isChecked ->
            // If cloud not configured, block enabling master switch
            val cloudConfigured = cloudStorageManager.isCloudStorageActive()
            if (isChecked && !cloudConfigured) {
                ToastUtils.warnToast(activity, rh.gs(R.string.cloud_connection_failed))
                allCloudSwitch.isChecked = false
                return@setOnCheckedChangeListener
            }
            applyAllCloudState(isChecked)
        }
        
        val dialog = AlertDialog.Builder(activity)
            .setTitle(rh.gs(R.string.export_options))
            .setView(dialogView)
            .setPositiveButton(rh.gs(app.aaps.core.ui.R.string.ok)) { _, _ ->
                // Save settings
                sp.putBoolean(PREF_ALL_CLOUD_ENABLED, allCloudSwitch.isChecked)
                sp.putBoolean(PREF_LOG_EMAIL_ENABLED, logEmailSwitch.isChecked)
                sp.putBoolean(PREF_LOG_CLOUD_ENABLED, logCloudSwitch.isChecked)
                sp.putBoolean(PREF_SETTINGS_LOCAL_ENABLED, settingsLocalCheckbox.isChecked)
                sp.putBoolean(PREF_SETTINGS_CLOUD_ENABLED, settingsCloudCheckbox.isChecked)
                sp.putBoolean(PREF_CSV_LOCAL_ENABLED, csvLocalSwitch.isChecked)
                sp.putBoolean(PREF_CSV_CLOUD_ENABLED, csvCloudSwitch.isChecked)
                
                onSettingsChanged()
                ToastUtils.infoToast(activity, rh.gs(R.string.export_options_updated))
            }
            .setNegativeButton(rh.gs(app.aaps.core.ui.R.string.cancel), null)
            .create()
        
        dialog.show()
    }
    
    /**
     * Set up mutual exclusivity between two switches - when one is turned on, the other is turned off
     */
    private fun setupMutualExclusivity(switch1: Switch, switch2: Switch) {
        switch1.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && switch2.isChecked) {
                switch2.isChecked = false
            }
            // Ensure at least one is checked
            if (!isChecked && !switch2.isChecked) {
                switch1.isChecked = true
            }
        }
        
        switch2.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && switch1.isChecked) {
                switch1.isChecked = false
            }
            // Ensure at least one is checked
            if (!isChecked && !switch1.isChecked) {
                switch2.isChecked = true
            }
        }
    }
    
    /**
     * Set up at-least-one-selected logic for two checkboxes
     * Both can be selected, but at least one must be selected
     */
    private fun setupAtLeastOneSelected(checkbox1: CheckBox, checkbox2: CheckBox) {
        checkbox1.setOnCheckedChangeListener { _, isChecked ->
            // Ensure at least one is checked
            if (!isChecked && !checkbox2.isChecked) {
                checkbox1.isChecked = true
            }
        }
        
        checkbox2.setOnCheckedChangeListener { _, isChecked ->
            // Ensure at least one is checked
            if (!isChecked && !checkbox1.isChecked) {
                checkbox2.isChecked = true
            }
        }
    }
    
    /**
     * Get current export destination preferences
     */
    fun isAllCloudEnabled(): Boolean {
        val value = sp.getBoolean(PREF_ALL_CLOUD_ENABLED, false)
        aapsLogger.info(LTag.CORE, "$LOG_PREFIX ExportDestination: isAllCloudEnabled=$value")
        return value
    }
    
    fun isLogCloudEnabled(): Boolean {
        val value = sp.getBoolean(PREF_LOG_CLOUD_ENABLED, false)
        aapsLogger.info(LTag.CORE, "$LOG_PREFIX ExportDestination: isLogCloudEnabled=$value")
        return value
    }
    
    fun isSettingsLocalEnabled(): Boolean {
        val value = sp.getBoolean(PREF_SETTINGS_LOCAL_ENABLED, true)
        aapsLogger.info(LTag.CORE, "$LOG_PREFIX ExportDestination: isSettingsLocalEnabled=$value")
        return value
    }
    
    fun isSettingsCloudEnabled(): Boolean {
        val value = sp.getBoolean(PREF_SETTINGS_CLOUD_ENABLED, false)
        aapsLogger.info(LTag.CORE, "$LOG_PREFIX ExportDestination: isSettingsCloudEnabled=$value")
        return value
    }
    
    /**
     * Check if both local and cloud are enabled for settings export
     */
    fun isSettingsBothEnabled(): Boolean {
        val localEnabled = sp.getBoolean(PREF_SETTINGS_LOCAL_ENABLED, true)
        val cloudEnabled = sp.getBoolean(PREF_SETTINGS_CLOUD_ENABLED, false)
        val bothEnabled = localEnabled && cloudEnabled
        aapsLogger.info(LTag.CORE, "$LOG_PREFIX ExportDestination: isSettingsBothEnabled=$bothEnabled (local=$localEnabled, cloud=$cloudEnabled)")
        return bothEnabled
    }
    
    fun isCsvCloudEnabled(): Boolean {
        val value = sp.getBoolean(PREF_CSV_CLOUD_ENABLED, false)
        aapsLogger.info(LTag.CORE, "$LOG_PREFIX ExportDestination: isCsvCloudEnabled=$value")
        return value
    }
    
    /**
     * Reset all export settings to local/email mode
     * - Disable "All Cloud"
     * - Set Log to "Email" (disable cloud)
     * - Set Settings to "Local" (disable cloud)
     * - Set CSV to "Local" (disable cloud)
     */
    fun resetToLocalSettings() {
        aapsLogger.info(LTag.CORE, "$LOG_PREFIX ExportDestination: Resetting all settings to local/email mode")
        sp.putBoolean(PREF_ALL_CLOUD_ENABLED, false)
        sp.putBoolean(PREF_LOG_EMAIL_ENABLED, true)
        sp.putBoolean(PREF_LOG_CLOUD_ENABLED, false)
        sp.putBoolean(PREF_SETTINGS_LOCAL_ENABLED, true)
        sp.putBoolean(PREF_SETTINGS_CLOUD_ENABLED, false)
        sp.putBoolean(PREF_CSV_LOCAL_ENABLED, true)
        sp.putBoolean(PREF_CSV_CLOUD_ENABLED, false)
    }
    
    /**
     * Enable "All Cloud" option for cloud export
     * - Enable "All Cloud" switch
     * - Set Log to "Cloud" (disable email)
     * - Set Settings to "Cloud" (disable local)
     * - Set CSV to "Cloud" (disable local)
     */
    fun enableAllCloud() {
        aapsLogger.info(LTag.CORE, "$LOG_PREFIX ExportDestination: Enabling all cloud mode")
        sp.putBoolean(PREF_ALL_CLOUD_ENABLED, true)
        sp.putBoolean(PREF_LOG_EMAIL_ENABLED, false)
        sp.putBoolean(PREF_LOG_CLOUD_ENABLED, true)
        sp.putBoolean(PREF_SETTINGS_LOCAL_ENABLED, true)
        sp.putBoolean(PREF_SETTINGS_CLOUD_ENABLED, true)
        sp.putBoolean(PREF_CSV_LOCAL_ENABLED, false)
        sp.putBoolean(PREF_CSV_CLOUD_ENABLED, true)
    }

    /**
     * Enable local storage for settings export
     * This is called when user declines cloud export but we still want to ensure local storage is enabled
     */
    fun enableLocalStorage() {
        aapsLogger.info(LTag.CORE, "$LOG_PREFIX ExportDestination: Enabling local storage")
        sp.putBoolean(PREF_SETTINGS_LOCAL_ENABLED, true)
    }
}
