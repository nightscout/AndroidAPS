package app.aaps.plugins.configuration.maintenance.cloud

import android.content.Intent
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import app.aaps.plugins.configuration.activities.DaggerAppCompatActivityWithResult
import androidx.lifecycle.lifecycleScope
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.plugins.configuration.R
import app.aaps.plugins.configuration.maintenance.MaintenancePlugin
import app.aaps.plugins.configuration.activities.SingleFragmentActivity
import app.aaps.core.interfaces.plugin.ActivePlugin
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import app.aaps.core.ui.dialogs.AlertDialogHelper
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri

/**
 * Dialog for selecting cloud directory.
 * 
 * This dialog is provider-agnostic and can be extended to support multiple cloud storage providers
 * in the future (e.g., Dropbox, OneDrive, etc.)
 */
@Singleton
class CloudDirectoryDialog @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val rh: ResourceHelper,
    private val cloudStorageManager: CloudStorageManager,
    private val maintenancePlugin: MaintenancePlugin,
    private val activePlugin: ActivePlugin,
    private val exportOptionsDialog: ExportOptionsDialog
) {
    
    companion object {
        private const val LOG_PREFIX = CloudConstants.LOG_PREFIX
    }
    
    /**
     * Show cloud directory dialog
     */
    fun showCloudDirectoryDialog(
        activity: DaggerAppCompatActivityWithResult,
        onLocalSelected: () -> Unit = { maintenancePlugin.selectAapsDirectory(activity) },
        onCloudSelected: () -> Unit = {},
        onStorageChanged: () -> Unit = {}
    ) {
        val dialogView = activity.layoutInflater.inflate(R.layout.dialog_cloud_directory, null)
        
        // UI elements
        val googleDriveRow = dialogView.findViewById<LinearLayout>(R.id.google_drive_row)
        val googleDriveRadio = dialogView.findViewById<RadioButton>(R.id.google_drive_radio)
        val clearAction = dialogView.findViewById<TextView>(R.id.clear_action)
        
        // Authorization status section UI elements
        val authStatusSection = dialogView.findViewById<LinearLayout>(R.id.authorization_status_section)
        val authStatusText = dialogView.findViewById<TextView>(R.id.authorization_status_text)
        val authCheckIcon = dialogView.findViewById<ImageView>(R.id.authorization_check_icon)
        val cloudPathText = dialogView.findViewById<TextView>(R.id.cloud_path_text)
        
        val currentType = cloudStorageManager.getActiveStorageType()
        val isCloudSelected = currentType == StorageTypes.GOOGLE_DRIVE

        // Initial state: set radio button based on current settings
        googleDriveRadio.isChecked = isCloudSelected
        
        // Update authorization status section
        updateAuthorizationStatusSection(
            activity,
            authStatusSection,
            authStatusText,
            authCheckIcon,
            cloudPathText
        )

        // Use MaterialAlertDialogBuilder with DialogTheme to match project style
        // Title is now in the layout itself, so we don't use setCustomTitle
        val dialog = MaterialAlertDialogBuilder(activity, app.aaps.core.ui.R.style.DialogTheme)
            .setView(dialogView)
            .setNegativeButton(rh.gs(app.aaps.core.ui.R.string.cancel), null)
            .create()
        
        // Top-right clear: equivalent to the original local storage clear action
        clearAction.setOnClickListener {
            val wasCloud = cloudStorageManager.isCloudStorageActive()
            val proceedClear: () -> Unit = {
                cloudStorageManager.clearAllCredentials()
                cloudStorageManager.setActiveStorageType(StorageTypes.LOCAL)
                cloudStorageManager.clearConnectionError()
                // Reset export destination settings to local/email mode
                exportOptionsDialog.resetToLocalSettings()
                onLocalSelected()
                onStorageChanged()
                ToastUtils.infoToast(activity, rh.gs(app.aaps.core.ui.R.string.success))
                // Reflect selection state on UI
                googleDriveRadio.isChecked = false
                dialog.dismiss()
            }
            if (wasCloud) {
                MaterialAlertDialogBuilder(activity, app.aaps.core.ui.R.style.DialogTheme)
                    .setCustomTitle(AlertDialogHelper.buildCustomTitle(activity, rh.gs(R.string.clear_cloud_settings)))
                    .setMessage(rh.gs(R.string.clear_cloud_settings_message))
                    .setPositiveButton(rh.gs(app.aaps.core.ui.R.string.yes)) { _, _ -> proceedClear() }
                    .setNegativeButton(rh.gs(app.aaps.core.ui.R.string.no), null)
                    .show()
            } else {
                proceedClear()
            }
        }
        
        // Google Drive row click
        val handleGoogleDriveClick = {
            googleDriveRadio.isChecked = true
            dialog.dismiss()
            handleCloudSelection(activity, StorageTypes.GOOGLE_DRIVE, onCloudSelected, onStorageChanged)
        }
        googleDriveRow.setOnClickListener { handleGoogleDriveClick() }
        googleDriveRadio.setOnClickListener { handleGoogleDriveClick() }
        
        dialog.show()
    }
    
    /** Handle cloud storage selection */
    private fun handleCloudSelection(
        activity: DaggerAppCompatActivityWithResult,
        storageType: String,
        onSuccess: () -> Unit,
        onStorageChanged: () -> Unit
    ) {
        activity.lifecycleScope.launch {
            try {
                val provider = cloudStorageManager.getProvider(storageType)
                if (provider == null) {
                    aapsLogger.error(LTag.CORE, "$LOG_PREFIX No provider available for $storageType")
                    ToastUtils.errorToast(activity, "Cloud provider not available")
                    return@launch
                }
                
                if (provider.hasValidCredentials()) {
                    if (provider.testConnection()) {
                        cloudStorageManager.setActiveStorageType(storageType)
                        // Automatically point to fixed settings directory
                        val folderId = provider.getOrCreateFolderPath(CloudConstants.CLOUD_PATH_SETTINGS)
                        if (!folderId.isNullOrEmpty()) provider.setSelectedFolderId(folderId)
                        onSuccess()
                        onStorageChanged()
                    } else {
                        showReauthorizeDialog(activity, storageType, onSuccess, onStorageChanged)
                    }
                } else {
                    startAuthFlow(activity, storageType, onSuccess, onStorageChanged)
                }
            } catch (e: Exception) {
                aapsLogger.error(LTag.CORE, "$LOG_PREFIX Error handling cloud selection", e)
                ToastUtils.errorToast(activity, rh.gs(R.string.cloud_auth_error, e.message ?: ""))
            }
        }
    }
    
    /**
     * Show dialog asking if user wants to enable cloud export after successful connection
     */
    private fun showEnableCloudExportDialog(
        activity: DaggerAppCompatActivityWithResult,
        onStorageChanged: () -> Unit
    ) {
        activity.runOnUiThread {
            MaterialAlertDialogBuilder(activity, app.aaps.core.ui.R.style.DialogTheme)
                .setCustomTitle(AlertDialogHelper.buildCustomTitle(activity, rh.gs(R.string.enable_cloud_export_title)))
                .setMessage(rh.gs(R.string.enable_cloud_export_message))
                .setPositiveButton(rh.gs(app.aaps.core.ui.R.string.yes)) { _, _ ->
                    exportOptionsDialog.enableAllCloud()
                    onStorageChanged()
                    ToastUtils.longInfoToast(activity, rh.gs(R.string.can_change_in_export_settings))
                }
                .setNegativeButton(rh.gs(app.aaps.core.ui.R.string.no)) { _, _ ->
                    // Still enable local storage even when user declines cloud export
                    exportOptionsDialog.enableLocalStorage()
                    ToastUtils.longInfoToast(activity, rh.gs(R.string.can_change_in_export_settings))
                }
                .show()
        }
    }
    
    /** Start auth flow for cloud provider */
    private suspend fun startAuthFlow(
        activity: DaggerAppCompatActivityWithResult,
        storageType: String,
        onSuccess: () -> Unit,
        onStorageChanged: () -> Unit
    ) {
        try {
            val provider = cloudStorageManager.getProvider(storageType) ?: return
            
            // Start OAuth/PKCE auth flow (provider-agnostic)
            val authUrl = provider.startAuth()
            if (authUrl == null) {
                ToastUtils.errorToast(activity, rh.gs(R.string.cloud_auth_start_failed))
                return
            }
            val customTabsIntent = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
            customTabsIntent.launchUrl(activity, authUrl.toUri())
            showWaitingDialog(activity, provider.displayName) { cancelled ->
                if (cancelled) {
                    ToastUtils.infoToast(activity, rh.gs(R.string.cloud_auth_cancelled))
                } else {
                    activity.lifecycleScope.launch {
                        val authCode = provider.waitForAuthCode(60000)
                        if (authCode != null) {
                            if (provider.completeAuth(authCode)) {
                                cloudStorageManager.setActiveStorageType(storageType)
                                ToastUtils.infoToast(activity, rh.gs(R.string.cloud_auth_success))
                                // Explicitly open maintenance page to prevent main page from stealing focus
                                openMaintenanceScreen(activity)
                                onSuccess()
                                onStorageChanged()
                                // Automatically point to fixed settings directory and skip selection
                                val folderId = provider.getOrCreateFolderPath(CloudConstants.CLOUD_PATH_SETTINGS)
                                if (!folderId.isNullOrEmpty()) provider.setSelectedFolderId(folderId)
                                // Ask user if they want to enable cloud export
                                showEnableCloudExportDialog(activity, onStorageChanged)
                            } else {
                                ToastUtils.errorToast(activity, rh.gs(R.string.cloud_auth_failed))
                            }
                        } else {
                            ToastUtils.errorToast(activity, rh.gs(R.string.cloud_auth_timeout))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            aapsLogger.error(LTag.CORE, "$LOG_PREFIX Error starting auth flow", e)
            ToastUtils.errorToast(activity, rh.gs(R.string.cloud_auth_error, e.message))
        }
    }

    /**
     * Explicitly open SingleFragmentActivity containing MaintenanceFragment, with a delayed
     * secondary attempt to prevent the browser closing/system returning to the previous app
     * from stealing focus back to MainActivity.
     */
    private fun openMaintenanceScreen(activity: DaggerAppCompatActivityWithResult) {
        try {
            val list = activePlugin.getPluginsList()
            val idx = list.indexOfFirst { it is MaintenancePlugin }
            if (idx >= 0) {
                val intent = Intent(activity, SingleFragmentActivity::class.java)
                    .setAction("CloudDirectoryDialog")
                    .putExtra("plugin", idx)
                    .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                activity.startActivity(intent)
                // Delay again to counteract possible late system foreground actions
                activity.window?.decorView?.postDelayed({
                    try { activity.startActivity(intent) } catch (_: Exception) { }
                }, 1200)
            } else {
                // Fallback: if index not found, still try to bring current Activity to foreground
                bringAppToForeground(activity)
            }
        } catch (_: Exception) { }
    }

    // Fallback method: bring app to foreground (using launch Intent, with double REORDER_TO_FRONT to stabilize focus)
    private fun bringAppToForeground(activity: DaggerAppCompatActivityWithResult) {
        try {
            val launchIntent = activity.packageManager.getLaunchIntentForPackage(activity.packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
            if (launchIntent != null) {
                activity.startActivity(launchIntent)
                activity.window?.decorView?.postDelayed({
                    try { activity.startActivity(launchIntent) } catch (_: Exception) { }
                }, 800)
            }
        } catch (_: Exception) { }
    }
    
    /** Waiting dialog for authorization */
    private fun showWaitingDialog(
        activity: DaggerAppCompatActivityWithResult,
        providerName: String,
        onResult: (cancelled: Boolean) -> Unit
    ) {
        val dialog = MaterialAlertDialogBuilder(activity, app.aaps.core.ui.R.style.DialogTheme)
            .setCustomTitle(AlertDialogHelper.buildCustomTitle(
                activity, 
                rh.gs(R.string.cloud_authorization_title, providerName)
            ))
            .setMessage(rh.gs(R.string.cloud_authorization_message, providerName))
            .setNegativeButton(rh.gs(app.aaps.core.ui.R.string.cancel)) { _, _ ->
                onResult(true)
            }
            .setCancelable(false)
            .create()
        
        dialog.show()
        
        // Immediately signal waiting started (dialog stays until flow finishes externally)
        onResult(false)
        dialog.dismiss()
    }
    
    /** Reauthorize dialog */
    private fun showReauthorizeDialog(
        activity: DaggerAppCompatActivityWithResult,
        storageType: String,
        onSuccess: () -> Unit,
        onStorageChanged: () -> Unit
    ) {
        MaterialAlertDialogBuilder(activity, app.aaps.core.ui.R.style.DialogTheme)
            .setCustomTitle(AlertDialogHelper.buildCustomTitle(
                activity, 
                rh.gs(R.string.cloud_connection_failed)
            ))
            .setMessage(rh.gs(R.string.cloud_reauthorize_message))
            .setPositiveButton(rh.gs(R.string.reauthorize)) { _, _ ->
                cloudStorageManager.clearAllCredentials()
                activity.lifecycleScope.launch {
                    startAuthFlow(activity, storageType, onSuccess, onStorageChanged)
                }
            }
            .setNegativeButton(rh.gs(app.aaps.core.ui.R.string.cancel), null)
            .show()
    }
    
    /**
     * Update authorization status section based on current cloud storage state
     */
    private fun updateAuthorizationStatusSection(
        activity: DaggerAppCompatActivityWithResult,
        authStatusSection: LinearLayout,
        authStatusText: TextView,
        authCheckIcon: ImageView,
        cloudPathText: TextView
    ) {
        val provider = cloudStorageManager.getProvider(StorageTypes.GOOGLE_DRIVE)
        val hasCredentials = provider?.hasValidCredentials() == true
        val hasConnectionError = cloudStorageManager.hasConnectionError()
        
        if (hasCredentials) {
            // Show authorization status section
            authStatusSection.visibility = View.VISIBLE
            
            if (hasConnectionError) {
                // Need re-authorization - use provider's resource ID
                authStatusText.text = rh.gs(provider.reAuthRequiredTextResId)
                authStatusText.setTextColor(activity.getColor(R.color.cloud_status_warning))
                authCheckIcon.setImageResource(R.drawable.ic_error)
                authCheckIcon.setColorFilter(activity.getColor(R.color.cloud_status_warning))
            } else {
                // Authorized successfully - use provider's resource ID
                authStatusText.text = rh.gs(provider.authorizedTextResId)
                authStatusText.setTextColor(activity.getColor(R.color.cloud_status_success))
                authCheckIcon.setImageResource(R.drawable.ic_meta_ok)
                authCheckIcon.setColorFilter(activity.getColor(R.color.cloud_status_success))
            }
            
            // Set cloud path
            cloudPathText.text = CloudConstants.CLOUD_PATH_EXPORT
        } else {
            // Hide authorization status section if no credentials
            authStatusSection.visibility = View.GONE
        }
    }
}
