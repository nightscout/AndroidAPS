package app.aaps.plugins.configuration.maintenance.cloud

import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.plugins.configuration.R
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dialog to let user choose import source when both local and cloud are enabled
 */
@Singleton
class ImportSourceDialog @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val rh: ResourceHelper,
    private val exportOptionsDialog: ExportOptionsDialog,
    private val cloudStorageManager: CloudStorageManager
) {
    
    enum class ImportSource {
        LOCAL,
        CLOUD
    }
    
    /**
     * Check if import source selection dialog should be shown
     * Returns true if both local and cloud are enabled for settings import
     */
    fun shouldShowSourceSelection(): Boolean {
        val localEnabled = exportOptionsDialog.isSettingsLocalEnabled()
        val cloudEnabled = exportOptionsDialog.isSettingsCloudEnabled()
        val isCloudActive = cloudStorageManager.isCloudStorageActive()
        
        // Show selection dialog only when both are enabled and cloud is properly configured
        val shouldShow = localEnabled && cloudEnabled && isCloudActive
        aapsLogger.info(LTag.CORE, "${CloudConstants.LOG_PREFIX} ImportSourceDialog: shouldShowSourceSelection=$shouldShow (local=$localEnabled, cloud=$cloudEnabled, cloudActive=$isCloudActive)")
        return shouldShow
    }
    
    /**
     * Get the single enabled import source if only one is enabled
     * Returns null if both are enabled (should show dialog)
     */
    fun getSingleEnabledSource(): ImportSource? {
        val localEnabled = exportOptionsDialog.isSettingsLocalEnabled()
        val cloudEnabled = exportOptionsDialog.isSettingsCloudEnabled()
        val isCloudActive = cloudStorageManager.isCloudStorageActive()
        
        return when {
            localEnabled && (!cloudEnabled || !isCloudActive) -> ImportSource.LOCAL
            cloudEnabled && isCloudActive && !localEnabled -> ImportSource.CLOUD
            else -> null // Both enabled, need to show dialog
        }
    }
    
    /**
     * Show import source selection dialog
     */
    fun showImportSourceDialog(
        activity: FragmentActivity,
        onSourceSelected: (ImportSource) -> Unit
    ) {
        aapsLogger.info(LTag.CORE, "${CloudConstants.LOG_PREFIX} ImportSourceDialog: Showing import source selection dialog")
        
        val dialogView = activity.layoutInflater.inflate(R.layout.dialog_import_source, null)
        
        val localStorageRow = dialogView.findViewById<LinearLayout>(R.id.local_storage_row)
        val cloudStorageRow = dialogView.findViewById<LinearLayout>(R.id.cloud_storage_row)
        
        val dialog = AlertDialog.Builder(activity)
            .setView(dialogView)
            .setNegativeButton(rh.gs(app.aaps.core.ui.R.string.cancel), null)
            .create()
        
        localStorageRow.setOnClickListener {
            aapsLogger.info(LTag.CORE, "${CloudConstants.LOG_PREFIX} ImportSourceDialog: User selected LOCAL")
            dialog.dismiss()
            onSourceSelected(ImportSource.LOCAL)
        }
        
        cloudStorageRow.setOnClickListener {
            aapsLogger.info(LTag.CORE, "${CloudConstants.LOG_PREFIX} ImportSourceDialog: User selected CLOUD")
            dialog.dismiss()
            onSourceSelected(ImportSource.CLOUD)
        }
        
        dialog.show()
    }
}
