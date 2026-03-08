package app.aaps.plugins.configuration.maintenance

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.work.ExistingWorkPolicy
import kotlinx.coroutines.launch
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.aaps.core.data.model.TE
import app.aaps.core.data.model.UE
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.androidPermissions.AndroidPermission
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.maintenance.FileListProvider
import app.aaps.core.interfaces.maintenance.ImportExportPrefs
import app.aaps.core.interfaces.maintenance.PrefMetadata
import app.aaps.core.interfaces.maintenance.PrefsFile
import app.aaps.core.interfaces.maintenance.PrefsMetadataKey
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.protection.ExportPasswordDataStore
import app.aaps.core.interfaces.protection.PasswordCheck
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventDiaconnG8PumpLogReset
import app.aaps.core.interfaces.rx.weardata.CwfData
import app.aaps.core.interfaces.rx.weardata.CwfMetadataKey
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.storage.Storage
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.userEntry.UserEntryPresentationHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.MidnightTime
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.extensions.asSettingsExport
import app.aaps.core.objects.workflow.LoggingWorker
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.core.ui.dialogs.TwoMessagesAlertDialog
import app.aaps.core.ui.dialogs.WarningDialog
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.core.utils.receivers.DataWorkerStorage
import app.aaps.plugins.configuration.R
import app.aaps.plugins.configuration.activities.DaggerAppCompatActivityWithResult
import app.aaps.plugins.configuration.maintenance.data.PrefFileNotFoundError
import app.aaps.plugins.configuration.maintenance.data.PrefIOError
import app.aaps.plugins.configuration.maintenance.data.Prefs
import app.aaps.plugins.configuration.maintenance.data.PrefsFormat
import app.aaps.plugins.configuration.maintenance.data.PrefsStatusImpl
import app.aaps.plugins.configuration.maintenance.dialogs.PrefImportSummaryDialog
import app.aaps.plugins.configuration.maintenance.formats.EncryptedPrefsFormat
import app.aaps.plugins.configuration.maintenance.cloud.CloudConstants
import app.aaps.plugins.configuration.maintenance.cloud.CloudStorageManager
import app.aaps.plugins.configuration.maintenance.cloud.StorageTypes
import app.aaps.plugins.configuration.maintenance.cloud.ExportOptionsDialog
import app.aaps.plugins.configuration.maintenance.cloud.ImportSourceDialog
import app.aaps.plugins.configuration.maintenance.PrefsMetadataKeyImpl
import app.aaps.plugins.configuration.maintenance.activities.CloudPrefImportListActivity
import app.aaps.shared.impl.weardata.ZipWatchfaceFormat
import dagger.Reusable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import kotlinx.coroutines.Dispatchers
import org.json.JSONObject
import java.io.FileNotFoundException
import java.io.IOException
import javax.inject.Inject

// Added for dialog callback explicit types
import android.content.DialogInterface
import androidx.appcompat.app.AlertDialog

/**
 * Created by mike on 03.07.2016.
 */

@Reusable
class ImportExportPrefsImpl @Inject constructor(
    private var aapsLogger: AAPSLogger,
    private val rh: ResourceHelper,
    private val sp: SP,
    private val preferences: Preferences,
    private val config: Config,
    private val persistenceLayer: PersistenceLayer,
    private val rxBus: RxBus,
    private val passwordCheck: PasswordCheck,
    private val exportPasswordDataStore: ExportPasswordDataStore,
    private val androidPermission: AndroidPermission,
    private val encryptedPrefsFormat: EncryptedPrefsFormat,
    private val prefFileList: FileListProvider,
    private val dateUtil: DateUtil,
    private val uiInteraction: UiInteraction,
    private val context: Context,
    private val dataWorkerStorage: DataWorkerStorage,
    private val activePlugin: ActivePlugin,
    private val configBuilder: ConfigBuilder,
    private val cloudStorageManager: CloudStorageManager,
    private val exportOptionsDialog: ExportOptionsDialog,
    private val importSourceDialog: ImportSourceDialog
) : ImportExportPrefs {

    companion object {
        var cloudPrefsFiles: List<PrefsFile> = emptyList()
        var cloudNextPageToken: String? = null
        var cloudTotalFilesCount: Int = 0  // Total count of settings files
    }

    override var selectedImportFile: PrefsFile? = null

    override fun prefsFileExists(): Boolean = prefFileList.listPreferenceFiles().isNotEmpty()
    private val disposable = CompositeDisposable()

    override fun exportSharedPreferences(f: Fragment) {
        f.activity?.let { exportSharedPreferences(it) }
    }

    override fun verifyStoragePermissions(fragment: Fragment, onGranted: Runnable) {
        fragment.context?.let { ctx ->
            val permission = ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_EXTERNAL_STORAGE)
            if (permission != PackageManager.PERMISSION_GRANTED) {
                // We don't have permission so prompt the user
                fragment.activity?.let {
                    androidPermission.askForPermission(it, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
                }
            } else onGranted.run()
        }
    }

    private fun prepareMetadata(context: Context): Map<PrefsMetadataKey, PrefMetadata> {

        val metadata: MutableMap<PrefsMetadataKey, PrefMetadata> = mutableMapOf()

        metadata[PrefsMetadataKeyImpl.DEVICE_NAME] = PrefMetadata(detectUserName(context), PrefsStatusImpl.OK)
        metadata[PrefsMetadataKeyImpl.CREATED_AT] = PrefMetadata(dateUtil.toISOString(dateUtil.now()), PrefsStatusImpl.OK)
        metadata[PrefsMetadataKeyImpl.AAPS_VERSION] = PrefMetadata(config.VERSION_NAME, PrefsStatusImpl.OK)
        metadata[PrefsMetadataKeyImpl.AAPS_FLAVOUR] = PrefMetadata(config.FLAVOR, PrefsStatusImpl.OK)
        metadata[PrefsMetadataKeyImpl.DEVICE_MODEL] = PrefMetadata(config.currentDeviceModelString, PrefsStatusImpl.OK)
        metadata[PrefsMetadataKeyImpl.ENCRYPTION] = PrefMetadata("Enabled", PrefsStatusImpl.OK)

        return metadata
    }

    @Suppress("SpellCheckingInspection")
    private fun detectUserName(context: Context): String {
        // based on https://medium.com/@pribble88/how-to-get-an-android-device-nickname-4b4700b3068c
        val n1 = Settings.System.getString(context.contentResolver, "bluetooth_name")
        val n3 = try {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter?.name
            } else null
        } catch (_: Exception) {
            null
        }
        val n4 = Settings.System.getString(context.contentResolver, "device_name")
        val n5 = Settings.Secure.getString(context.contentResolver, "lock_screen_owner_info")
        val n6 = Settings.Global.getString(context.contentResolver, "device_name")

        // name provided (hopefully) by user
        val patientName = preferences.get(StringKey.GeneralPatientName)
        val defaultPatientName = rh.gs(app.aaps.core.ui.R.string.patient_name_default)

        // name we detect from OS
        val systemName = n1 ?: n3 ?: n4 ?: n5 ?: n6 ?: defaultPatientName
        return if (patientName.isNotEmpty() && patientName != defaultPatientName) patientName else systemName
    }

    private fun askForMasterPass(activity: FragmentActivity, @StringRes canceledMsg: Int, then: ((password: String) -> Unit)) {
        passwordCheck.queryPassword(activity, app.aaps.core.ui.R.string.master_password, StringKey.ProtectionMasterPassword, { password ->
            then(password)
        }, {
                                        ToastUtils.warnToast(activity, rh.gs(canceledMsg))
                                    })
    }

    @Suppress("SameParameterValue")
    private fun askForEncryptionPass(
        activity: FragmentActivity, @StringRes canceledMsg: Int, @StringRes passwordName: Int, @StringRes passwordExplanation: Int?,
        @StringRes passwordWarning: Int?, then: ((password: String) -> Unit)
    ) {
        passwordCheck.queryAnyPassword(activity, passwordName, StringKey.ProtectionMasterPassword, passwordExplanation, passwordWarning, { password ->
            then(password)
        }, {
                                           ToastUtils.warnToast(activity, rh.gs(canceledMsg))
                                       })
    }

    @Suppress("SameParameterValue")
    private fun askForMasterPassIfNeeded(activity: FragmentActivity, @StringRes canceledMsg: Int, then: ((password: String) -> Unit)) {
        askForMasterPass(activity, canceledMsg, then)
    }

    private fun assureMasterPasswordSet(activity: FragmentActivity, @StringRes wrongPwdTitle: Int): Boolean {
        if (preferences.getIfExists(StringKey.ProtectionMasterPassword).isNullOrEmpty()) {
            WarningDialog.showWarning(
                activity, rh.gs(wrongPwdTitle), rh.gs(R.string.master_password_missing, rh.gs(R.string.protection)), R.string.nav_preferences,
                { activity.startActivity(Intent(activity, uiInteraction.preferencesActivity).putExtra(UiInteraction.PREFERENCE, UiInteraction.Preferences.PROTECTION)) }
            )
            exportPasswordDataStore.clearPasswordDataStore(context)
            return false
        }
        return true
    }

    /***
     * Ask to confirm export unless a valid password is already available
     */
    private fun askToConfirmExport(activity: FragmentActivity, fileToExport: DocumentFile, then: ((password: String) -> Unit)) {
        if (!assureMasterPasswordSet(activity, app.aaps.core.ui.R.string.nav_export)) {
            return
        }

        // Get password from datastore
        val (password, isExpired, isAboutToExpire) = exportPasswordDataStore.getPasswordFromDataStore(context)
        if (password.isNotEmpty() && !(isExpired || isAboutToExpire)) {
            // We have an (encrypted) password in the phones DataStore that is not expired or about to expire (third)
            then(password)
            return // No need to ask.
        }

        // Make sure stored password is properly reset
        exportPasswordDataStore.clearPasswordDataStore((context))

        // Ask for entering password and store when successfully entered
        TwoMessagesAlertDialog.showAlert(
            activity, rh.gs(app.aaps.core.ui.R.string.nav_export),
            rh.gs(R.string.export_to) + " " + fileToExport.name + "?",
            rh.gs(R.string.password_preferences_encrypt_prompt), {
                askForMasterPassIfNeeded(activity, R.string.preferences_export_canceled)
                { password ->
                    then(exportPasswordDataStore.putPasswordToDataStore(context, password))
                }
            }, null, R.drawable.ic_header_export
        )
    }

    // Added: Confirmation dialog for non-filename targets (e.g., Google Drive) that can force password prompt
    private fun askToConfirmExport(activity: FragmentActivity, targetDisplayName: String, forcePrompt: Boolean = false, then: ((password: String) -> Unit)) {
        if (!assureMasterPasswordSet(activity, app.aaps.core.ui.R.string.nav_export)) return

        if (!forcePrompt) {
            val (password, isExpired, isAboutToExpire) = exportPasswordDataStore.getPasswordFromDataStore(context)
            if (password.isNotEmpty() && !(isExpired || isAboutToExpire)) {
                then(password)
                return
            }
        }
        exportPasswordDataStore.clearPasswordDataStore(context)

        TwoMessagesAlertDialog.showAlert(
            activity,
            rh.gs(app.aaps.core.ui.R.string.nav_export),
            rh.gs(R.string.export_to) + " " + targetDisplayName + " ?",
            rh.gs(R.string.password_preferences_encrypt_prompt),
            {
                askForMasterPassIfNeeded(activity, R.string.preferences_export_canceled) { pwd ->
                    then(exportPasswordDataStore.putPasswordToDataStore(context, pwd))
                }
            },
            null,
            R.drawable.ic_header_export
        )
    }

    private fun askToConfirmImport(activity: FragmentActivity, fileToImport: PrefsFile, then: ((password: String) -> Unit)) {
        if (!assureMasterPasswordSet(activity, R.string.import_setting)) return
        TwoMessagesAlertDialog.showAlert(
            activity, rh.gs(R.string.import_setting),
            rh.gs(R.string.import_from) + " " + fileToImport.name + "?",
            rh.gs(app.aaps.core.ui.R.string.password_preferences_decrypt_prompt), {
                askForMasterPass(activity, R.string.preferences_import_canceled, then)
            }, null, R.drawable.ic_header_import
        )
    }

    private fun promptForDecryptionPasswordIfNeeded(
        activity: FragmentActivity, prefs: Prefs, importOk: Boolean,
        format: PrefsFormat, importFile: PrefsFile, then: ((prefs: Prefs, importOk: Boolean) -> Unit)
    ) {

        // current master password was not the one used for decryption, so we prompt for old password...
        if (!importOk && (prefs.metadata[PrefsMetadataKeyImpl.ENCRYPTION]?.status == PrefsStatusImpl.ERROR)) {
            askForEncryptionPass(
                activity, R.string.preferences_import_canceled, R.string.old_master_password,
                R.string.different_password_used, R.string.master_password_will_be_replaced
            ) { password ->

                // ...and use it to load & decrypt file again
                val prefsReloaded = format.loadPreferences(importFile.content, password)
                prefsReloaded.metadata = prefFileList.checkMetadata(prefsReloaded.metadata)

                // import is OK when we do not have errors (warnings are allowed)
                val importOkCheckedAgain = checkIfImportIsOk(prefsReloaded)

                then(prefsReloaded, importOkCheckedAgain)
            }
        } else {
            then(prefs, importOk)
        }
    }

    /**
     * Save preferences to file
     */
    private fun savePreferences(newFile: DocumentFile, password: String): Boolean {
        var resultOk = false // Assume result was not OK unless acknowledged

        try {
            val entries: MutableMap<String, String> = mutableMapOf()
            for ((key, value) in sp.getAll()) {
                if (preferences.isExportableKey(key))
                    entries[key] = value.toString()
                else
                    aapsLogger.warn(LTag.CORE, "Not exportable key: $key $value")
            }
            val prefs = Prefs(entries, prepareMetadata(context))
            encryptedPrefsFormat.savePreferences(newFile, prefs, password)
            resultOk = true // Assuming export was executed successfully (or it would have thrown an exception)

        } catch (e: FileNotFoundException) {
            aapsLogger.error(LTag.CORE, "Unhandled exception: file not found", e)
        } catch (e: IOException) {
            aapsLogger.error(LTag.CORE, "Unhandled exception: IO exception", e)
        } catch (e: PrefFileNotFoundError) {
            aapsLogger.error(LTag.CORE, "File system exception: Pref File not found, export canceled", e)
        } catch (e: PrefIOError) {
            aapsLogger.error(LTag.CORE, "File system exception: PrefIOError, export canceled", e)
        }
        aapsLogger.debug(LTag.CORE, "savePreferences: $resultOk")
        return resultOk
    }

    private fun exportSharedPreferences(activity: FragmentActivity) {
        // Check export destination preference for user settings
        val localEnabled = exportOptionsDialog.isSettingsLocalEnabled()
        val cloudEnabled = exportOptionsDialog.isSettingsCloudEnabled()
        val isCloudActive = cloudStorageManager.isCloudStorageActive()
        
        // Determine export destinations
        val exportToLocal = localEnabled
        val exportToCloud = cloudEnabled && isCloudActive
        
        aapsLogger.info(LTag.CORE, "${CloudConstants.LOG_PREFIX} EXPORT exportToLocal=$exportToLocal, exportToCloud=$exportToCloud")
        
        if (exportToLocal && exportToCloud) {
            // Export to both: local first, then cloud
            exportToBoth(activity)
            return
        }
        
        if (exportToCloud) {
            exportToCloud(activity)
            return
        }

        // Local export requires AAPS base directory
        val directoryUri = preferences.getIfExists(StringKey.AapsDirectoryUri)
        if (directoryUri.isNullOrEmpty()) {
            ToastUtils.errorToast(activity, rh.gs(R.string.error_accessing_filesystem_select_aaps_directory_properly))
            return
        }
        exportToLocal(activity)
    }
    
    /**
     * Export to both local and cloud storage
     * First export to local, then to cloud
     */
    private fun exportToBoth(activity: FragmentActivity) {
        // Check local directory first
        val directoryUri = preferences.getIfExists(StringKey.AapsDirectoryUri)
        if (directoryUri.isNullOrEmpty()) {
            ToastUtils.errorToast(activity, rh.gs(R.string.error_accessing_filesystem_select_aaps_directory_properly))
            return
        }
        
        prefFileList.ensureExportDirExists()
        val newFile = prefFileList.newPreferenceFile()
        
        if (newFile == null) {
            ToastUtils.errorToast(activity, rh.gs(R.string.exported_failed))
            return
        }

        // Ask password once, then export to both destinations
        askToConfirmExport(activity, newFile) { password ->
            // Export to local first
            doExportToLocal(activity, newFile, password)
            // Then export to cloud
            doExportToCloud(activity, password)
        }
    }
    
    private fun exportToLocal(activity: FragmentActivity) {
        prefFileList.ensureExportDirExists()
        val newFile = prefFileList.newPreferenceFile()
        
        if (newFile == null) {
            ToastUtils.errorToast(activity, rh.gs(R.string.exported_failed))
            return
        }

        askToConfirmExport(activity, newFile) { password ->
            doExportToLocal(activity, newFile, password)
        }
    }
    
    /**
     * Perform local export without password prompt
     */
    private fun doExportToLocal(activity: FragmentActivity, newFile: DocumentFile, password: String) {
        val exportResultMessage = if (savePreferences(newFile, password))
            rh.gs(R.string.exported)
        else
            rh.gs(R.string.exported_failed)

        ToastUtils.okToast(activity, exportResultMessage)

        disposable += persistenceLayer.insertPumpTherapyEventIfNewByTimestamp(
            therapyEvent = TE.asSettingsExport(error = exportResultMessage),
            timestamp = dateUtil.now(),
            action = Action.EXPORT_SETTINGS,
            source = Sources.Automation,
            note = "Manual Local: $exportResultMessage",
            listValues = listOf()
        ).subscribe()
    }
    
    private fun exportToCloud(activity: FragmentActivity) {
        activity.lifecycleScope.launch {
            // Pre-check cloud connection before asking for password
            val provider = cloudStorageManager.getActiveProvider()
            if (provider == null) {
                aapsLogger.error(LTag.CORE, "${CloudConstants.LOG_PREFIX} EXPORT_NO_PROVIDER")
                ToastUtils.errorToast(activity, rh.gs(R.string.cloud_connection_failed))
                return@launch
            }
            
            if (!provider.testConnection()) {
                aapsLogger.error(LTag.CORE, "${CloudConstants.LOG_PREFIX} EXPORT_CONN_FAIL")
                ToastUtils.errorToast(activity, rh.gs(R.string.cloud_connection_failed))
                return@launch
            }

            // Create temp file for password prompt display
            val tempDir = prefFileList.ensureTempDirExists()
            if (tempDir == null) {
                aapsLogger.error(LTag.CORE, "${CloudConstants.LOG_PREFIX} EXPORT_NO_TEMP_DIR")
                ToastUtils.errorToast(activity, rh.gs(R.string.exported_failed))
                return@launch
            }

            val timeLocal = org.joda.time.LocalDateTime.now().toString(org.joda.time.format.DateTimeFormat.forPattern("yyyy-MM-dd'_'HHmmss"))
            val exportFileName = "${timeLocal}_${config.FLAVOR}.json"
            val tempDoc = tempDir.createFile("application/json", exportFileName)
            if (tempDoc == null) {
                aapsLogger.error(LTag.CORE, "${CloudConstants.LOG_PREFIX} EXPORT_CREATE_TEMP_FAIL")
                ToastUtils.errorToast(activity, rh.gs(R.string.exported_failed))
                return@launch
            }

            askToConfirmExport(activity, tempDoc) { password ->
                // Delete the temp file created for prompt, doExportToCloud will create its own
                tempDoc.delete()
                doExportToCloud(activity, password)
            }
        }
    }
    
    /**
     * Perform cloud export without password prompt
     */
    private fun doExportToCloud(activity: FragmentActivity, password: String) {
        activity.lifecycleScope.launch {
            try {
                val provider = cloudStorageManager.getActiveProvider()
                if (provider == null) {
                    aapsLogger.error(LTag.CORE, "${CloudConstants.LOG_PREFIX} EXPORT_NO_PROVIDER")
                    ToastUtils.errorToast(activity, rh.gs(R.string.cloud_connection_failed))
                    return@launch
                }
                
                if (!provider.testConnection()) {
                    aapsLogger.error(LTag.CORE, "${CloudConstants.LOG_PREFIX} EXPORT_CONN_FAIL")
                    ToastUtils.errorToast(activity, rh.gs(R.string.cloud_connection_failed))
                    return@launch
                }

                val tempDir = prefFileList.ensureTempDirExists()
                if (tempDir == null) {
                    aapsLogger.error(LTag.CORE, "${CloudConstants.LOG_PREFIX} EXPORT_NO_TEMP_DIR")
                    ToastUtils.errorToast(activity, rh.gs(R.string.export_to_cloud_failed))
                    return@launch
                }

                val timeLocal = org.joda.time.LocalDateTime.now().toString(org.joda.time.format.DateTimeFormat.forPattern("yyyy-MM-dd'_'HHmmss"))
                val exportFileName = "${timeLocal}_${config.FLAVOR}.json"
                val tempDoc = tempDir.createFile("application/json", exportFileName)
                if (tempDoc == null) {
                    aapsLogger.error(LTag.CORE, "${CloudConstants.LOG_PREFIX} EXPORT_CREATE_TEMP_FAIL")
                    ToastUtils.errorToast(activity, rh.gs(R.string.export_to_cloud_failed))
                    return@launch
                }

                val saved = savePreferences(tempDoc, password)
                if (!saved) {
                    aapsLogger.error(LTag.CORE, "${CloudConstants.LOG_PREFIX} EXPORT_SAVE_PREFS_FAIL")
                    ToastUtils.errorToast(activity, rh.gs(R.string.export_to_cloud_failed))
                    tempDoc.delete()
                    return@launch
                }

                val bytes = activity.contentResolver.openInputStream(tempDoc.uri)?.use { it.readBytes() }
                if (bytes == null) {
                    aapsLogger.error(LTag.CORE, "${CloudConstants.LOG_PREFIX} EXPORT_READ_TEMP_FAIL")
                    ToastUtils.errorToast(activity, rh.gs(R.string.export_to_cloud_failed))
                    tempDoc.delete()
                    return@launch
                }

                provider.getOrCreateFolderPath(CloudConstants.CLOUD_PATH_SETTINGS)?.let {
                    provider.setSelectedFolderId(it)
                }

                ToastUtils.longInfoToast(context, rh.gs(R.string.uploading_to_cloud))

                var uploadedFileId = provider.uploadFileToPath(
                    exportFileName, bytes, "application/json", CloudConstants.CLOUD_PATH_SETTINGS
                )
                if (uploadedFileId == null) {
                    uploadedFileId = provider.uploadFile(exportFileName, bytes, "application/json")
                }

                val exportResultMessage = if (uploadedFileId != null) {
                    rh.gs(R.string.exported_to_cloud) + "\n" + rh.gs(R.string.cloud_directory_path, CloudConstants.CLOUD_PATH_SETTINGS)
                } else {
                    rh.gs(R.string.export_to_cloud_failed)
                }

                ToastUtils.infoToast(activity, exportResultMessage)

                disposable += persistenceLayer.insertPumpTherapyEventIfNewByTimestamp(
                    therapyEvent = TE.asSettingsExport(error = exportResultMessage),
                    timestamp = dateUtil.now(),
                    action = Action.EXPORT_SETTINGS,
                    source = Sources.Automation,
                    note = "Manual Cloud: $exportResultMessage",
                    listValues = listOf()
                ).subscribe()

                tempDoc.delete()
            } catch (e: Exception) {
                aapsLogger.error(LTag.CORE, "${CloudConstants.LOG_PREFIX} EXPORT_EXCEPTION", e)
                ToastUtils.errorToast(activity, rh.gs(R.string.export_to_cloud_failed))
            }
        }
    }

    override fun exportSharedPreferencesNonInteractive(context: Context, password: String): Boolean {
        // Check export destination preferences (same logic as manual export)
        val localEnabled = exportOptionsDialog.isSettingsLocalEnabled()
        val cloudEnabled = exportOptionsDialog.isSettingsCloudEnabled()
        val isCloudActive = cloudStorageManager.isCloudStorageActive()
        
        val exportToLocal = localEnabled
        val exportToCloud = cloudEnabled && isCloudActive
        
        aapsLogger.info(LTag.CORE, "${CloudConstants.LOG_PREFIX} NONINTERACTIVE_EXPORT exportToLocal=$exportToLocal, exportToCloud=$exportToCloud")
        
        // Export to local if enabled
        var localResult = true
        if (exportToLocal) {
            prefFileList.ensureExportDirExists()
            val newFile = prefFileList.newPreferenceFile()
            if (newFile != null) {
                localResult = savePreferences(newFile, password)
                aapsLogger.info(LTag.CORE, "${CloudConstants.LOG_PREFIX} NONINTERACTIVE_EXPORT_LOCAL result=$localResult")
            } else {
                aapsLogger.error(LTag.CORE, "${CloudConstants.LOG_PREFIX} NONINTERACTIVE_EXPORT_LOCAL_NO_FILE")
                localResult = false
            }
        }
        
        // Export to cloud if enabled
        if (exportToCloud) {
            kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                try {
                    val provider = cloudStorageManager.getActiveProvider()
                    if (provider == null) {
                        aapsLogger.error(LTag.CORE, "${CloudConstants.LOG_PREFIX} NONINTERACTIVE_EXPORT_NO_PROVIDER")
                        return@launch
                    }
                    
                    if (!provider.testConnection()) {
                        aapsLogger.error(LTag.CORE, "${CloudConstants.LOG_PREFIX} NONINTERACTIVE_EXPORT_CONN_FAIL")
                        return@launch
                    }
                    
                    val tempDir = prefFileList.ensureTempDirExists()
                    if (tempDir == null) {
                        aapsLogger.error(LTag.CORE, "${CloudConstants.LOG_PREFIX} NONINTERACTIVE_EXPORT_NO_TEMP_DIR")
                        return@launch
                    }
                    
                    val timeLocal = org.joda.time.LocalDateTime.now().toString(org.joda.time.format.DateTimeFormat.forPattern("yyyy-MM-dd'_'HHmmss"))
                    val fileName = "${timeLocal}_${config.FLAVOR}.json"
                    val tempDoc = tempDir.createFile("application/json", fileName)
                    if (tempDoc == null) {
                        aapsLogger.error(LTag.CORE, "${CloudConstants.LOG_PREFIX} NONINTERACTIVE_EXPORT_CREATE_TEMP_FAIL")
                        return@launch
                    }
                    
                    val saved = savePreferences(tempDoc, password)
                    if (!saved) {
                        aapsLogger.error(LTag.CORE, "${CloudConstants.LOG_PREFIX} NONINTERACTIVE_EXPORT_SAVE_TEMP_FAIL")
                        tempDoc.delete()
                        return@launch
                    }
                    
                    val fileContent = tempDoc.uri.let { uri ->
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    }
                    
                    tempDoc.delete()
                    
                    if (fileContent != null) {
                        // Use uploadFileToPath for consistent folder structure
                        var uploadedFileId = provider.uploadFileToPath(
                            fileName, fileContent, "application/json", CloudConstants.CLOUD_PATH_SETTINGS
                        )
                        if (uploadedFileId == null) {
                            uploadedFileId = provider.uploadFile(fileName, fileContent, "application/json")
                        }
                        
                        if (uploadedFileId != null) {
                            aapsLogger.info(LTag.CORE, "${CloudConstants.LOG_PREFIX} NONINTERACTIVE_EXPORT_CLOUD_OK fileName=$fileName fileId=$uploadedFileId")
                        } else {
                            aapsLogger.error(LTag.CORE, "${CloudConstants.LOG_PREFIX} NONINTERACTIVE_EXPORT_CLOUD_FAIL")
                        }
                    } else {
                        aapsLogger.error(LTag.CORE, "${CloudConstants.LOG_PREFIX} NONINTERACTIVE_EXPORT_READ_FILE_FAIL")
                    }
                } catch (e: Exception) {
                    aapsLogger.error(LTag.CORE, "${CloudConstants.LOG_PREFIX} NONINTERACTIVE_EXPORT_EXCEPTION", e)
                }
            }
        }
        
        // Return true if at least one export method succeeded or was started
        return if (exportToLocal && exportToCloud) {
            localResult // Cloud is async, return local result
        } else if (exportToCloud) {
            true // Cloud export started (async)
        } else {
            localResult // Only local export
        }
    }

    override fun importSharedPreferences(activity: FragmentActivity) {
        // Check if both local and cloud are enabled - show selection dialog
        if (importSourceDialog.shouldShowSourceSelection()) {
            importSourceDialog.showImportSourceDialog(activity) { source ->
                when (source) {
                    ImportSourceDialog.ImportSource.LOCAL -> importFromLocal(activity)
                    ImportSourceDialog.ImportSource.CLOUD -> importFromCloud(activity)
                }
            }
            return
        }
        
        // Only one source enabled - use it directly
        val singleSource = importSourceDialog.getSingleEnabledSource()
        when (singleSource) {
            ImportSourceDialog.ImportSource.CLOUD -> {
                importFromCloud(activity)
                return
            }
            ImportSourceDialog.ImportSource.LOCAL, null -> {
                importFromLocal(activity)
            }
        }
    }
    
    private fun importFromLocal(activity: FragmentActivity) {
        // Local import requires AAPS base directory
        val directoryUri = preferences.getIfExists(StringKey.AapsDirectoryUri)
        if (directoryUri.isNullOrEmpty()) {
            ToastUtils.errorToast(activity, rh.gs(R.string.error_accessing_filesystem_select_aaps_directory_properly))
            return
        }

        try {
            if (activity is DaggerAppCompatActivityWithResult)
                activity.callForPrefFile?.launch(null)
        } catch (e: IllegalArgumentException) {
            // this exception happens on some early implementations of ActivityResult contracts
            // when registered and called for the second time
            ToastUtils.errorToast(activity, rh.gs(R.string.goto_main_try_again))
            aapsLogger.error(LTag.CORE, "Internal android framework exception", e)
        }
    }

    private fun importFromCloud(activity: FragmentActivity) {
        // Show loading indicator
        val progressDialog = AlertDialog.Builder(activity)
            .setTitle(rh.gs(R.string.import_from_cloud))
            .setMessage(rh.gs(R.string.loading_from_cloud))
            .setCancelable(false)
            .create()
        progressDialog.show()
        
        activity.lifecycleScope.launch {
            try {
                val provider = cloudStorageManager.getActiveProvider()
                if (provider == null) {
                    progressDialog.dismiss()
                    ToastUtils.errorToast(activity, rh.gs(R.string.cloud_connection_failed))
                    return@launch
                }
                
                if (!provider.testConnection()) {
                    progressDialog.dismiss()
                    ToastUtils.errorToast(activity, rh.gs(R.string.cloud_connection_failed))
                    return@launch
                }
                // Auto-locate to fixed settings folder as list source, fall back to selected/root if failed
                val settingsFolderId = provider.getOrCreateFolderPath(CloudConstants.CLOUD_PATH_SETTINGS)
                aapsLogger.info(LTag.CORE, "${CloudConstants.LOG_PREFIX} IMPORT_CLOUD getOrCreateFolderPath(${CloudConstants.CLOUD_PATH_SETTINGS}) returned: $settingsFolderId")
                if (!settingsFolderId.isNullOrEmpty()) {
                    provider.setSelectedFolderId(settingsFolderId)
                    aapsLogger.info(LTag.CORE, "${CloudConstants.LOG_PREFIX} IMPORT_CLOUD setSelectedFolderId: $settingsFolderId")
                } else {
                    aapsLogger.warn(LTag.CORE, "${CloudConstants.LOG_PREFIX} IMPORT_CLOUD getOrCreateFolderPath returned null/empty, will use default folder")
                }
                
                ToastUtils.infoToast(activity, rh.gs(R.string.cloud_directory_path, CloudConstants.CLOUD_PATH_SETTINGS))

                // Count total files first using the provider interface
                cloudTotalFilesCount = provider.countSettingsFiles()
                
                val page = provider.listSettingsFiles(pageSize = CloudConstants.DEFAULT_PAGE_SIZE, pageToken = null)
                val files = page.files
                cloudNextPageToken = page.nextPageToken
                if (files.isEmpty()) {
                    progressDialog.dismiss()
                    ToastUtils.warnToast(activity, rh.gs(R.string.no_settings_files_found))
                    return@launch
                }

                // Filter files matching yyyy-MM-dd_HHmmss*.json pattern
                val namePattern = Regex("^\\d{4}-\\d{2}-\\d{2}_\\d{6}.*\\.json$", RegexOption.IGNORE_CASE)
                val matchingFiles = files.filter { f -> namePattern.containsMatchIn(f.name) }

                // Download all files and parse metadata, just like local files
                val prefsFiles = mutableListOf<PrefsFile>()
                var processedFiles = 0
                for (file in matchingFiles) {
                    try {
                        // Update progress
                        progressDialog.setMessage(rh.gs(R.string.loading_file_progress, file.name, processedFiles + 1, matchingFiles.size))
                        
                        val bytes = provider.downloadFile(file.id)
                        if (bytes != null) {
                            val content = String(bytes, Charsets.UTF_8)
                            // Parse file metadata
                            val metadata = encryptedPrefsFormat.loadMetadata(content)
                            val prefsFile = PrefsFile(file.name, content, metadata)
                            prefsFiles.add(prefsFile)
                        }
                    } catch (e: Exception) {
                        aapsLogger.warn(LTag.CORE, "Failed to load metadata for ${file.name}", e)
                        // If metadata parsing fails, still add the file but without metadata
                        try {
                            val bytes = provider.downloadFile(file.id)
                            if (bytes != null) {
                                val content = String(bytes, Charsets.UTF_8)
                                val prefsFile = PrefsFile(file.name, content, emptyMap())
                                prefsFiles.add(prefsFile)
                            }
                        } catch (e2: Exception) {
                            aapsLogger.error(LTag.CORE, "Failed to download ${file.name}", e2)
                        }
                    }
                    processedFiles++
                }

                progressDialog.dismiss()

                if (prefsFiles.isEmpty()) {
                    ToastUtils.warnToast(activity, rh.gs(R.string.no_settings_files_found))
                    return@launch
                }

                // Use CloudPrefImportListActivity to display file details
                cloudPrefsFiles = prefsFiles // Store file list temporarily
                val intent = Intent(activity, CloudPrefImportListActivity::class.java)
                if (activity is DaggerAppCompatActivityWithResult) {
                    activity.startActivityForResult(intent, CloudConstants.CLOUD_IMPORT_REQUEST_CODE)
                }
                
            } catch (e: Exception) {
                progressDialog.dismiss()
                aapsLogger.error(LTag.CORE, "Cloud import init failed", e)
                ToastUtils.errorToast(activity, rh.gs(R.string.cloud_folder_error))
            }
        }
    }

    override fun importCustomWatchface(fragment: Fragment) {
        fragment.activity?.let { importCustomWatchface(it) }
    }

    override fun importCustomWatchface(activity: FragmentActivity) {
        try {
            if (activity is DaggerAppCompatActivityWithResult)
                activity.callForCustomWatchfaceFile?.launch(null)
        } catch (e: IllegalArgumentException) {
            // this exception happens on some early implementations of ActivityResult contracts
            // when registered and called for the second time
            ToastUtils.errorToast(activity, rh.gs(R.string.goto_main_try_again))
            aapsLogger.error(LTag.CORE, "Internal android framework exception", e)
        }
    }

    override fun exportCustomWatchface(customWatchface: CwfData, withDate: Boolean) {
        prefFileList.ensureExportDirExists()
        val newFile = prefFileList.newCwfFile(customWatchface.metadata[CwfMetadataKey.CWF_FILENAME] ?: "", withDate) ?: return
        ZipWatchfaceFormat.saveCustomWatchface(context.contentResolver, newFile, customWatchface)
    }

    // Do not pass full file through intent. It crash on large file
    // override fun importSharedPreferences(activity: FragmentActivity, importFile: PrefsFile) {
    override fun doImportSharedPreferences(activity: FragmentActivity) {

        // File should be prepared here
        val importFile = selectedImportFile ?: return

        askToConfirmImport(activity, importFile) { password ->

            val format: PrefsFormat = encryptedPrefsFormat

            try {

                val prefsAttempted = format.loadPreferences(importFile.content, password)
                prefsAttempted.metadata = prefFileList.checkMetadata(prefsAttempted.metadata)

                // import is OK when we do not have errors (warnings are allowed)
                val importOkAttempted = checkIfImportIsOk(prefsAttempted)

                promptForDecryptionPasswordIfNeeded(activity, prefsAttempted, importOkAttempted, format, importFile) { prefs, importOk ->

                    // if at end we allow to import preferences
                    val importPossible = (importOk || config.isEngineeringMode()) && (prefs.values.isNotEmpty())

                    PrefImportSummaryDialog.showSummary(activity, importOk, importPossible, prefs, {
                        if (importPossible) {
                            activePlugin.beforeImport()
                            sp.clear()
                            for ((key, value) in prefs.values) {
                                if (value == "true" || value == "false") {
                                    sp.putBoolean(key, value.toBoolean())
                                } else {
                                    sp.putString(key, value)
                                }
                            }
                            
                            // All settings including Google Drive settings and export destination preferences
                            // are now imported from backup file. If tokens are invalid, user can re-authorize.
                            
                            activePlugin.afterImport()
                            restartAppAfterImport(activity)
                        } else {
                            // for impossible imports it should not be called
                            ToastUtils.errorToast(activity, rh.gs(R.string.preferences_import_impossible))
                        }
                    })

                }

            } catch (e: PrefFileNotFoundError) {
                ToastUtils.errorToast(activity, rh.gs(R.string.filenotfound) + " " + importFile)
                aapsLogger.error(LTag.CORE, "Unhandled exception", e)
            } catch (e: PrefIOError) {
                aapsLogger.error(LTag.CORE, "Unhandled exception", e)
                ToastUtils.errorToast(activity, e.message)
            }
        }
    }

    private fun checkIfImportIsOk(prefs: Prefs): Boolean {
        var importOk = true

        for ((_, value) in prefs.metadata) {
            if (value.status == PrefsStatusImpl.ERROR)
                importOk = false
        }
        return importOk
    }

    private fun restartAppAfterImport(context: Context) {
        rxBus.send(EventDiaconnG8PumpLogReset())
        preferences.put(BooleanKey.GeneralSetupWizardProcessed, true)
        OKDialog.show(context, rh.gs(R.string.setting_imported), rh.gs(R.string.restartingapp)) {
            if (context is AppCompatActivity) {
                context.finish()
            }
            configBuilder.exitApp("Import", Sources.Maintenance, false)
        }
    }

    override fun exportUserEntriesCsv(activity: FragmentActivity) {
        aapsLogger.info(LTag.CORE, "${CloudConstants.LOG_PREFIX} CSV_EXPORT exportUserEntriesCsv called, enqueuing WorkManager")
        WorkManager.getInstance(activity).enqueueUniqueWork(
            "export",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequest.Builder(CsvExportWorker::class.java).build()
        )
        aapsLogger.info(LTag.CORE, "${CloudConstants.LOG_PREFIX} CSV_EXPORT WorkManager enqueued")
    }

    class CsvExportWorker(
        private val context: Context,
        params: WorkerParameters
    ) : LoggingWorker(context, params, Dispatchers.IO) {

        @Inject lateinit var rh: ResourceHelper
        @Inject lateinit var prefFileList: FileListProvider
        @Inject lateinit var userEntryPresentationHelper: UserEntryPresentationHelper
        @Inject lateinit var storage: Storage
        @Inject lateinit var persistenceLayer: PersistenceLayer
        @Inject lateinit var cloudStorageManager: CloudStorageManager
        @Inject lateinit var exportOptionsDialog: ExportOptionsDialog

        override suspend fun doWorkAndLog(): Result {
            aapsLogger.info(LTag.CORE, "${CloudConstants.LOG_PREFIX} CSV_EXPORT doWorkAndLog started")
            
            val entries: List<UE> = try {
                persistenceLayer.getUserEntryFilteredDataFromTime(MidnightTime.calc() - T.days(90).msecs()).blockingGet()
            } catch (e: Exception) {
                aapsLogger.error(LTag.CORE, "CSV_EXPORT Error reading user entries from database", e)
                ToastUtils.longErrorToast(context, rh.gs(R.string.csv_upload_error) + ": Database read error")
                return Result.failure(workDataOf("Error" to "Database read error: ${e.message}"))
            }
            
            aapsLogger.info(LTag.CORE, "${CloudConstants.LOG_PREFIX} CSV_EXPORT entries count=${entries.size}")
            
            val isCsvCloudEnabled = exportOptionsDialog.isCsvCloudEnabled()
            val isCloudActive = cloudStorageManager.isCloudStorageActive()
            aapsLogger.info(LTag.CORE, "${CloudConstants.LOG_PREFIX} CSV_EXPORT isCsvCloudEnabled=$isCsvCloudEnabled, isCloudActive=$isCloudActive")
            
            if (isCsvCloudEnabled && isCloudActive) {
                aapsLogger.info(LTag.CORE, "${CloudConstants.LOG_PREFIX} CSV_EXPORT calling exportToCloud")
                return exportToCloud(entries)
            } else {
                aapsLogger.info(LTag.CORE, "${CloudConstants.LOG_PREFIX} CSV_EXPORT calling exportToLocal")
                return exportToLocal(entries)
            }
        }
        
        private suspend fun exportToLocal(userEntries: List<UE>): Result {
            prefFileList.ensureExportDirExists()
            val newFile = prefFileList.newExportCsvFile() ?: return Result.failure()
            var ret = Result.success()
            try {
                saveCsv(newFile, userEntries)
                ToastUtils.okToast(context, rh.gs(R.string.ue_exported))
            } catch (e: FileNotFoundException) {
                ToastUtils.errorToast(context, rh.gs(R.string.filenotfound) + " " + newFile)
                aapsLogger.error(LTag.CORE, "Unhandled exception", e)
                ret = Result.failure(workDataOf("Error" to "Error FileNotFoundException"))
            } catch (e: IOException) {
                ToastUtils.errorToast(context, e.message)
                aapsLogger.error(LTag.CORE, "Unhandled exception", e)
                ret = Result.failure(workDataOf("Error" to "Error IOException"))
            }
            return ret
        }
        
        private suspend fun exportToCloud(userEntries: List<UE>): Result {
            aapsLogger.info(LTag.CORE, "${CloudConstants.LOG_PREFIX} CSV_EXPORT_CLOUD started with ${userEntries.size} entries")
            try {
                val provider = cloudStorageManager.getActiveProvider()
                if (provider == null) {
                    aapsLogger.error(LTag.CORE, "${CloudConstants.LOG_PREFIX} CSV_EXPORT_CLOUD no active provider")
                    ToastUtils.longErrorToast(context, rh.gs(R.string.csv_upload_failed))
                    return Result.failure(workDataOf("Error" to "No active cloud provider"))
                }
                
                val contents = userEntryPresentationHelper.userEntriesToCsv(userEntries)
                val fileName = "UserEntries_${org.joda.time.LocalDateTime.now().toString(org.joda.time.format.DateTimeFormat.forPattern("yyyy-MM-dd_HHmmss"))}.csv"
                aapsLogger.info(LTag.CORE, "${CloudConstants.LOG_PREFIX} CSV_EXPORT_CLOUD fileName=$fileName, contents length=${contents.length}")
                
                // First locate selected folder to fixed path
                val folderId = provider.getOrCreateFolderPath(CloudConstants.CLOUD_PATH_USER_ENTRIES)
                aapsLogger.info(LTag.CORE, "${CloudConstants.LOG_PREFIX} CSV_EXPORT_CLOUD folderId=$folderId")
                folderId?.let { provider.setSelectedFolderId(it) }

                ToastUtils.longInfoToast(context, rh.gs(R.string.uploading_to_cloud))
                aapsLogger.info(LTag.CORE, "${CloudConstants.LOG_PREFIX} CSV_EXPORT_CLOUD uploading...")

                var uploadedFileId = provider.uploadFileToPath(
                    fileName,
                    contents.toByteArray(Charsets.UTF_8),
                    "text/csv",
                    CloudConstants.CLOUD_PATH_USER_ENTRIES
                )
                aapsLogger.info(LTag.CORE, "${CloudConstants.LOG_PREFIX} CSV_EXPORT_CLOUD uploadFileToPath result=$uploadedFileId")
                
                if (uploadedFileId == null) {
                    aapsLogger.info(LTag.CORE, "${CloudConstants.LOG_PREFIX} CSV_EXPORT_CLOUD trying fallback uploadFile")
                    uploadedFileId = provider.uploadFile(fileName, contents.toByteArray(Charsets.UTF_8), "text/csv")
                    aapsLogger.info(LTag.CORE, "${CloudConstants.LOG_PREFIX} CSV_EXPORT_CLOUD fallback uploadFile result=$uploadedFileId")
                }
                
                if (uploadedFileId != null) {
                    aapsLogger.info(LTag.CORE, "${CloudConstants.LOG_PREFIX} CSV_EXPORT_CLOUD SUCCESS")
                    ToastUtils.okToast(context, rh.gs(R.string.csv_uploaded_to_cloud) + "\n" + rh.gs(R.string.cloud_directory_path, CloudConstants.CLOUD_PATH_USER_ENTRIES), isShort = false)
                    return Result.success()
                } else {
                    aapsLogger.error(LTag.CORE, "${CloudConstants.LOG_PREFIX} CSV_EXPORT_CLOUD FAILED - uploadedFileId is null")
                    ToastUtils.longErrorToast(context, rh.gs(R.string.csv_upload_failed))
                    return Result.failure(workDataOf("Error" to "Cloud upload failed"))
                }
            } catch (e: Exception) {
                aapsLogger.error(LTag.CORE, "${CloudConstants.LOG_PREFIX} CSV_EXPORT_CLOUD EXCEPTION", e)
                ToastUtils.longErrorToast(context, rh.gs(R.string.csv_upload_error))
                return Result.failure(workDataOf("Error" to "Exception: ${e.message}"))
            }
        }

        private fun saveCsv(file: DocumentFile, userEntries: List<UE>) {
            try {
                val contents = userEntryPresentationHelper.userEntriesToCsv(userEntries)
                storage.putFileContents(context.contentResolver, file, contents)
            } catch (_: FileNotFoundException) {
                throw PrefFileNotFoundError(file.name ?: "UNKNOWN")
            } catch (_: IOException) {
                throw PrefIOError(file.name ?: "UNKNOWN")
            } catch (_: SecurityException) {
                ToastUtils.errorToast(context, rh.gs(R.string.error_accessing_filesystem_select_aaps_directory_properly))
                throw PrefFileNotFoundError(file.name ?: "UNKNOWN")
            }
        }
    }

    override fun exportApsResult(algorithm: String?, input: JSONObject, output: JSONObject?) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            "export",
            ExistingWorkPolicy.APPEND,
            OneTimeWorkRequest.Builder(ApsResultExportWorker::class.java)
                .setInputData(dataWorkerStorage.storeInputData(ApsResultExportWorker.ApsResultData(algorithm, input, output)))
                .build()
        )
    }

    class ApsResultExportWorker(
        context: Context,
        params: WorkerParameters
    ) : LoggingWorker(context, params, Dispatchers.IO) {

        @Inject lateinit var prefFileList: FileListProvider
        @Inject lateinit var storage: Storage
        @Inject lateinit var config: Config
        @Inject lateinit var dataWorkerStorage: DataWorkerStorage

        data class ApsResultData(val algorithm: String?, val input: JSONObject, val output: JSONObject?)

        override suspend fun doWorkAndLog(): Result {
            if (!config.isEngineeringMode()) return Result.success(workDataOf("Result" to "Export not enabled"))
            val apsResultData = dataWorkerStorage.pickupObject(inputData.getLong(DataWorkerStorage.STORE_KEY, -1)) as ApsResultData?
                ?: return Result.failure(workDataOf("Error" to "missing input data"))

            prefFileList.ensureResultDirExists()
            val newFile = prefFileList.newResultFile()
            var ret = Result.success()
            try {
                val jsonObject = JSONObject().apply {
                    put("algorithm", apsResultData.algorithm)
                    put("input", apsResultData.input)
                    put("output", apsResultData.output)
                }
                storage.putFileContents(newFile, jsonObject.toString())
            } catch (e: FileNotFoundException) {
                aapsLogger.error(LTag.CORE, "Unhandled exception", e)
                ret = Result.failure(workDataOf("Error" to "Error FileNotFoundException"))
            } catch (e: IOException) {
                aapsLogger.error(LTag.CORE, "Unhandled exception", e)
                ret = Result.failure(workDataOf("Error" to "Error IOException"))
            }
            return ret
        }
    }
}