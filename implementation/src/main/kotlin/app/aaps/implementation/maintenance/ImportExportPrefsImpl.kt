package app.aaps.implementation.maintenance

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.core.app.ActivityCompat
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.aaps.core.data.model.TE
import app.aaps.core.data.model.UE
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.di.ApplicationScope
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.maintenance.ExportConfig
import app.aaps.core.interfaces.maintenance.ExportDestination
import app.aaps.core.interfaces.maintenance.ExportPreparation
import app.aaps.core.interfaces.maintenance.ExportResult
import app.aaps.core.interfaces.maintenance.FileListProvider
import app.aaps.core.interfaces.maintenance.ImportDecryptResult
import app.aaps.core.interfaces.maintenance.ImportExportPrefs
import app.aaps.core.interfaces.maintenance.PrefMetadata
import app.aaps.core.interfaces.maintenance.Prefs
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
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.extensions.asSettingsExport
import app.aaps.core.objects.workflow.LoggingWorker
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.core.utils.receivers.DataWorkerStorage
import app.aaps.implementation.R
import app.aaps.implementation.maintenance.cloud.CloudConstants
import app.aaps.implementation.maintenance.cloud.CloudStorageManager
import app.aaps.implementation.maintenance.data.PrefFileNotFoundError
import app.aaps.implementation.maintenance.data.PrefIOError
import app.aaps.implementation.maintenance.data.PrefsFormat
import app.aaps.implementation.maintenance.data.PrefsStatusImpl
import app.aaps.implementation.maintenance.formats.EncryptedPrefsFormat
import app.aaps.shared.impl.weardata.ZipWatchfaceFormat
import dagger.Reusable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.FileNotFoundException
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Created by mike on 03.07.2016.
 */

private fun filenameTimestamp(): String =
    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss"))

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
    private val encryptedPrefsFormat: EncryptedPrefsFormat,
    private val prefFileList: FileListProvider,
    private val dateUtil: DateUtil,
    private val uiInteraction: UiInteraction,
    private val context: Context,
    private val dataWorkerStorage: DataWorkerStorage,
    private val activePlugin: ActivePlugin,
    @ApplicationScope private val appScope: CoroutineScope,
    private val cloudStorageManager: CloudStorageManager,
    private val userEntryPresentationHelper: UserEntryPresentationHelper,
    private val storage: Storage
) : ImportExportPrefs {

    private var pendingExportFile: DocumentFile? = null

    // Compose export support — discrete steps

    override fun isMasterPasswordSet(): Boolean =
        !preferences.getIfExists(StringKey.ProtectionMasterPassword).isNullOrEmpty()

    override fun getExportConfig(): ExportConfig {
        val isCloudActive = cloudStorageManager.isCloudStorageActive()
        val provider = cloudStorageManager.getActiveProvider()
        val hasCloudError = isCloudActive && (provider?.hasConnectionError() == true || provider?.hasValidCredentials() != true)
        return ExportConfig(
            isCloudActive = isCloudActive,
            isCloudError = hasCloudError,
            hasCloudCredentials = cloudStorageManager.hasAnyCloudCredentials(),
            settingsLocal = sp.getBoolean(ExportPrefKeys.PREF_SETTINGS_LOCAL_ENABLED, true),
            settingsCloud = sp.getBoolean(ExportPrefKeys.PREF_SETTINGS_CLOUD_ENABLED, false),
            logEmail = sp.getBoolean(ExportPrefKeys.PREF_LOG_EMAIL_ENABLED, true),
            logCloud = sp.getBoolean(ExportPrefKeys.PREF_LOG_CLOUD_ENABLED, false),
            csvLocal = sp.getBoolean(ExportPrefKeys.PREF_CSV_LOCAL_ENABLED, true),
            csvCloud = sp.getBoolean(ExportPrefKeys.PREF_CSV_CLOUD_ENABLED, false),
            cloudDisplayName = provider?.displayName
        )
    }

    override fun setSettingsLocalEnabled(enabled: Boolean) {
        sp.putBoolean(ExportPrefKeys.PREF_SETTINGS_LOCAL_ENABLED, enabled)
        // Ensure at least one destination is selected
        if (!enabled && !sp.getBoolean(ExportPrefKeys.PREF_SETTINGS_CLOUD_ENABLED, false)) {
            sp.putBoolean(ExportPrefKeys.PREF_SETTINGS_CLOUD_ENABLED, true)
        }
        sp.putBoolean(ExportPrefKeys.PREF_ALL_CLOUD_ENABLED, false)
    }

    override fun setSettingsCloudEnabled(enabled: Boolean) {
        sp.putBoolean(ExportPrefKeys.PREF_SETTINGS_CLOUD_ENABLED, enabled)
        // Ensure at least one destination is selected
        if (!enabled && !sp.getBoolean(ExportPrefKeys.PREF_SETTINGS_LOCAL_ENABLED, true)) {
            sp.putBoolean(ExportPrefKeys.PREF_SETTINGS_LOCAL_ENABLED, true)
        }
        sp.putBoolean(ExportPrefKeys.PREF_ALL_CLOUD_ENABLED, false)
    }

    override fun setLogEmailEnabled(enabled: Boolean) {
        sp.putBoolean(ExportPrefKeys.PREF_LOG_EMAIL_ENABLED, enabled)
        if (!enabled && !sp.getBoolean(ExportPrefKeys.PREF_LOG_CLOUD_ENABLED, false)) {
            sp.putBoolean(ExportPrefKeys.PREF_LOG_CLOUD_ENABLED, true)
        }
        sp.putBoolean(ExportPrefKeys.PREF_ALL_CLOUD_ENABLED, false)
    }

    override fun setLogCloudEnabled(enabled: Boolean) {
        sp.putBoolean(ExportPrefKeys.PREF_LOG_CLOUD_ENABLED, enabled)
        if (!enabled && !sp.getBoolean(ExportPrefKeys.PREF_LOG_EMAIL_ENABLED, true)) {
            sp.putBoolean(ExportPrefKeys.PREF_LOG_EMAIL_ENABLED, true)
        }
        sp.putBoolean(ExportPrefKeys.PREF_ALL_CLOUD_ENABLED, false)
    }

    override fun setCsvLocalEnabled(enabled: Boolean) {
        sp.putBoolean(ExportPrefKeys.PREF_CSV_LOCAL_ENABLED, enabled)
        if (!enabled && !sp.getBoolean(ExportPrefKeys.PREF_CSV_CLOUD_ENABLED, false)) {
            sp.putBoolean(ExportPrefKeys.PREF_CSV_CLOUD_ENABLED, true)
        }
        sp.putBoolean(ExportPrefKeys.PREF_ALL_CLOUD_ENABLED, false)
    }

    override fun setCsvCloudEnabled(enabled: Boolean) {
        sp.putBoolean(ExportPrefKeys.PREF_CSV_CLOUD_ENABLED, enabled)
        if (!enabled && !sp.getBoolean(ExportPrefKeys.PREF_CSV_LOCAL_ENABLED, true)) {
            sp.putBoolean(ExportPrefKeys.PREF_CSV_LOCAL_ENABLED, true)
        }
        sp.putBoolean(ExportPrefKeys.PREF_ALL_CLOUD_ENABLED, false)
    }

    override fun prepareExport(): ExportPreparation? {
        val config = getExportConfig()
        val localEnabled = config.settingsLocal
        val cloudEnabled = config.settingsCloud && config.isCloudActive

        val destination = when {
            localEnabled && cloudEnabled -> ExportDestination.BOTH
            cloudEnabled                 -> ExportDestination.CLOUD
            else                         -> ExportDestination.LOCAL
        }

        // For LOCAL / BOTH we need a local file
        if (destination != ExportDestination.CLOUD) {
            prefFileList.ensureExportDirExists()
            val newFile = prefFileList.newPreferenceFile() ?: return null
            pendingExportFile = newFile
        }

        val (password, isExpired, isAboutToExpire) = exportPasswordDataStore.getPasswordFromDataStore(context)
        val cachedPassword = if (password.isNotEmpty() && !(isExpired || isAboutToExpire)) password else {
            exportPasswordDataStore.clearPasswordDataStore(context)
            null
        }

        val displayFileName = when (destination) {
            ExportDestination.CLOUD -> config.cloudDisplayName ?: "Cloud"
            ExportDestination.BOTH  -> (pendingExportFile?.name ?: "unknown") + " + " + (config.cloudDisplayName ?: "Cloud")
            ExportDestination.LOCAL -> pendingExportFile?.name ?: "unknown"
        }

        return ExportPreparation(
            fileName = displayFileName,
            cachedPassword = cachedPassword,
            destination = destination,
            cloudDisplayName = config.cloudDisplayName
        )
    }

    override suspend fun executeExport(password: String): ExportResult {
        val config = getExportConfig()
        val localEnabled = config.settingsLocal
        val cloudEnabled = config.settingsCloud && config.isCloudActive

        val destination = when {
            localEnabled && cloudEnabled -> ExportDestination.BOTH
            cloudEnabled                 -> ExportDestination.CLOUD
            else                         -> ExportDestination.LOCAL
        }

        var localSuccess: Boolean? = null
        var cloudSuccess: Boolean? = null

        // Local export
        if (destination == ExportDestination.LOCAL || destination == ExportDestination.BOTH) {
            val file = pendingExportFile
            if (file != null) {
                pendingExportFile = null
                localSuccess = savePreferences(file, password)
                val resultMessage = if (localSuccess) rh.gs(R.string.exported) else rh.gs(R.string.exported_failed)
                persistenceLayer.insertPumpTherapyEventIfNewByTimestamp(
                    therapyEvent = TE.asSettingsExport(error = resultMessage),
                    timestamp = dateUtil.now(),
                    action = Action.EXPORT_SETTINGS,
                    source = Sources.Automation,
                    note = "Manual Local: $resultMessage",
                    listValues = listOf()
                )
            } else {
                localSuccess = false
            }
        }

        // Cloud export
        if (destination == ExportDestination.CLOUD || destination == ExportDestination.BOTH) {
            cloudSuccess = performCloudExport(password)
            val resultMessage = if (cloudSuccess) rh.gs(R.string.exported_to_cloud) else rh.gs(R.string.export_to_cloud_failed)
            persistenceLayer.insertPumpTherapyEventIfNewByTimestamp(
                therapyEvent = TE.asSettingsExport(error = resultMessage),
                timestamp = dateUtil.now(),
                action = Action.EXPORT_SETTINGS,
                source = Sources.Automation,
                note = "Manual Cloud: $resultMessage",
                listValues = listOf()
            )
        }

        return ExportResult(localSuccess = localSuccess, cloudSuccess = cloudSuccess)
    }

    /**
     * Perform cloud export without UI interaction.
     * Reuses logic from doExportToCloud() but works without an Activity.
     */
    private suspend fun performCloudExport(password: String): Boolean {
        try {
            val provider = cloudStorageManager.getActiveProvider()
            if (provider == null) {
                aapsLogger.error(LTag.CORE, "${CloudConstants.LOG_PREFIX} COMPOSE_EXPORT_NO_PROVIDER")
                return false
            }
            if (!provider.testConnection()) {
                aapsLogger.error(LTag.CORE, "${CloudConstants.LOG_PREFIX} COMPOSE_EXPORT_CONN_FAIL")
                return false
            }
            val tempDir = prefFileList.ensureTempDirExists()
            if (tempDir == null) {
                aapsLogger.error(LTag.CORE, "${CloudConstants.LOG_PREFIX} COMPOSE_EXPORT_NO_TEMP_DIR")
                return false
            }
            val timeLocal = filenameTimestamp()
            val exportFileName = "${timeLocal}_${config.FLAVOR}.json"
            val tempDoc = tempDir.createFile("application/json", exportFileName)
            if (tempDoc == null) {
                aapsLogger.error(LTag.CORE, "${CloudConstants.LOG_PREFIX} COMPOSE_EXPORT_CREATE_TEMP_FAIL")
                return false
            }
            val saved = savePreferences(tempDoc, password)
            if (!saved) {
                aapsLogger.error(LTag.CORE, "${CloudConstants.LOG_PREFIX} COMPOSE_EXPORT_SAVE_FAIL")
                tempDoc.delete()
                return false
            }
            val bytes = context.contentResolver.openInputStream(tempDoc.uri)?.use { it.readBytes() }
            if (bytes == null) {
                aapsLogger.error(LTag.CORE, "${CloudConstants.LOG_PREFIX} COMPOSE_EXPORT_READ_FAIL")
                tempDoc.delete()
                return false
            }
            provider.getOrCreateFolderPath(CloudConstants.CLOUD_PATH_SETTINGS)?.let {
                provider.setSelectedFolderId(it)
            }
            var uploadedFileId = provider.uploadFileToPath(
                exportFileName, bytes, "application/json", CloudConstants.CLOUD_PATH_SETTINGS
            )
            if (uploadedFileId == null) {
                uploadedFileId = provider.uploadFile(exportFileName, bytes, "application/json")
            }
            tempDoc.delete()
            return uploadedFileId != null
        } catch (e: Exception) {
            aapsLogger.error(LTag.CORE, "${CloudConstants.LOG_PREFIX} COMPOSE_EXPORT_EXCEPTION", e)
            return false
        }
    }

    override fun cacheExportPassword(password: String): String =
        exportPasswordDataStore.putPasswordToDataStore(context, password)

    // Legacy export — uses dialogs via uiInteraction (kept for old UI)

    override fun exportSharedPreferences(activity: FragmentActivity) {
        exportSharedPreferencesLegacy(activity)
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
        passwordCheck.queryPassword(activity, app.aaps.core.keys.R.string.master_password, StringKey.ProtectionMasterPassword, { password ->
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
            uiInteraction.showError(
                context = activity,
                title = rh.gs(wrongPwdTitle),
                message = rh.gs(app.aaps.core.ui.R.string.master_password_missing, rh.gs(app.aaps.core.ui.R.string.protection)),
                positiveButton = app.aaps.core.ui.R.string.nav_preferences,
                ok = { activity.startActivity(Intent(activity, uiInteraction.preferencesActivity).putExtra(UiInteraction.PREFERENCE, UiInteraction.Preferences.PROTECTION)) }
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
        uiInteraction.showOkCancelDialog(
            context = activity, title = rh.gs(app.aaps.core.ui.R.string.nav_export),
            message = rh.gs(app.aaps.core.ui.R.string.export_to) + " " + fileToExport.name + "?",
            secondMessage = rh.gs(app.aaps.core.ui.R.string.password_preferences_encrypt_prompt), ok = {
                askForMasterPassIfNeeded(activity, app.aaps.core.ui.R.string.preferences_export_canceled)
                { password ->
                    then(exportPasswordDataStore.putPasswordToDataStore(context, password))
                }
            },
            icon = R.drawable.ic_header_export
        )
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

    private fun exportSharedPreferencesLegacy(activity: FragmentActivity) {
        // Check export destination preference for user settings
        val localEnabled = sp.getBoolean(ExportPrefKeys.PREF_SETTINGS_LOCAL_ENABLED, true)
        val cloudEnabled = sp.getBoolean(ExportPrefKeys.PREF_SETTINGS_CLOUD_ENABLED, false)
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

        appScope.launch {
            persistenceLayer.insertPumpTherapyEventIfNewByTimestamp(
                therapyEvent = TE.asSettingsExport(error = exportResultMessage),
                timestamp = dateUtil.now(),
                action = Action.EXPORT_SETTINGS,
                source = Sources.Automation,
                note = "Manual Local: $exportResultMessage",
                listValues = listOf()
            )
        }
    }

    private fun exportToCloud(activity: FragmentActivity) {
        activity.lifecycleScope.launch {
            // Pre-check cloud connection before asking for password
            val provider = cloudStorageManager.getActiveProvider()
            if (provider == null) {
                aapsLogger.error(LTag.CORE, "${CloudConstants.LOG_PREFIX} EXPORT_NO_PROVIDER")
                ToastUtils.errorToast(activity, rh.gs(app.aaps.core.ui.R.string.cloud_connection_failed))
                return@launch
            }

            if (!provider.testConnection()) {
                aapsLogger.error(LTag.CORE, "${CloudConstants.LOG_PREFIX} EXPORT_CONN_FAIL")
                ToastUtils.errorToast(activity, rh.gs(app.aaps.core.ui.R.string.cloud_connection_failed))
                return@launch
            }

            // Create temp file for password prompt display
            val tempDir = prefFileList.ensureTempDirExists()
            if (tempDir == null) {
                aapsLogger.error(LTag.CORE, "${CloudConstants.LOG_PREFIX} EXPORT_NO_TEMP_DIR")
                ToastUtils.errorToast(activity, rh.gs(R.string.exported_failed))
                return@launch
            }

            val timeLocal = filenameTimestamp()
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
                    ToastUtils.errorToast(activity, rh.gs(app.aaps.core.ui.R.string.cloud_connection_failed))
                    return@launch
                }

                if (!provider.testConnection()) {
                    aapsLogger.error(LTag.CORE, "${CloudConstants.LOG_PREFIX} EXPORT_CONN_FAIL")
                    ToastUtils.errorToast(activity, rh.gs(app.aaps.core.ui.R.string.cloud_connection_failed))
                    return@launch
                }

                val tempDir = prefFileList.ensureTempDirExists()
                if (tempDir == null) {
                    aapsLogger.error(LTag.CORE, "${CloudConstants.LOG_PREFIX} EXPORT_NO_TEMP_DIR")
                    ToastUtils.errorToast(activity, rh.gs(R.string.export_to_cloud_failed))
                    return@launch
                }

                val timeLocal = filenameTimestamp()
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

                persistenceLayer.insertPumpTherapyEventIfNewByTimestamp(
                    therapyEvent = TE.asSettingsExport(error = exportResultMessage),
                    timestamp = dateUtil.now(),
                    action = Action.EXPORT_SETTINGS,
                    source = Sources.Automation,
                    note = "Manual Cloud: $exportResultMessage",
                    listValues = listOf()
                )

                tempDoc.delete()
            } catch (e: Exception) {
                aapsLogger.error(LTag.CORE, "${CloudConstants.LOG_PREFIX} EXPORT_EXCEPTION", e)
                ToastUtils.errorToast(activity, rh.gs(R.string.export_to_cloud_failed))
            }
        }
    }

    override fun exportSharedPreferencesNonInteractive(context: Context, password: String): Boolean {
        // Check export destination preferences (same logic as manual export)
        val localEnabled = sp.getBoolean(ExportPrefKeys.PREF_SETTINGS_LOCAL_ENABLED, true)
        val cloudEnabled = sp.getBoolean(ExportPrefKeys.PREF_SETTINGS_CLOUD_ENABLED, false)
        val isCloudActive = cloudStorageManager.isCloudStorageActive()

        val exportToCloud = cloudEnabled && isCloudActive

        aapsLogger.info(LTag.CORE, "${CloudConstants.LOG_PREFIX} NONINTERACTIVE_EXPORT exportToLocal=$localEnabled, exportToCloud=$exportToCloud")

        // Export to local if enabled
        var localResult = true
        if (localEnabled) {
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
            appScope.launch(Dispatchers.IO) {
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

                    val timeLocal = filenameTimestamp()
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
        return if (localEnabled && exportToCloud) {
            localResult // Cloud is async, return local result
        } else if (exportToCloud) {
            true // Cloud export started (async)
        } else {
            localResult // Only local export
        }
    }

    override fun exportCustomWatchface(customWatchface: CwfData, withDate: Boolean) {
        prefFileList.ensureExportDirExists()
        val newFile = prefFileList.newCwfFile(customWatchface.metadata[CwfMetadataKey.CWF_FILENAME] ?: "", withDate) ?: return
        ZipWatchfaceFormat.saveCustomWatchface(context.contentResolver, newFile, customWatchface)
    }

    private fun checkIfImportIsOk(prefs: Prefs): Boolean {
        var importOk = true

        for ((_, value) in prefs.metadata) {
            if (value.status == PrefsStatusImpl.ERROR)
                importOk = false
        }
        return importOk
    }

    // Compose import support — discrete steps, no UI

    override suspend fun getLocalImportFiles(): List<PrefsFile> =
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            prefFileList.listPreferenceFiles()
        }

    override suspend fun getCloudImportFiles(pageToken: String?): Pair<List<PrefsFile>, String?> =
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            val provider = cloudStorageManager.getActiveProvider()
                ?: return@withContext Pair(emptyList(), null)

            if (!provider.testConnection()) {
                return@withContext Pair(emptyList(), null)
            }

            val settingsFolderId = provider.getOrCreateFolderPath(CloudConstants.CLOUD_PATH_SETTINGS)
            if (!settingsFolderId.isNullOrEmpty()) {
                provider.setSelectedFolderId(settingsFolderId)
            }

            val page = provider.listSettingsFiles(
                pageSize = CloudConstants.DEFAULT_PAGE_SIZE,
                pageToken = pageToken
            )

            val namePattern = Regex("^\\d{4}-\\d{2}-\\d{2}_\\d{6}.*\\.json$", RegexOption.IGNORE_CASE)
            val matchingFiles = page.files.filter { f -> namePattern.containsMatchIn(f.name) }

            val prefsFiles = mutableListOf<PrefsFile>()
            for (file in matchingFiles) {
                try {
                    val bytes = provider.downloadFile(file.id)
                    if (bytes != null) {
                        val content = String(bytes, Charsets.UTF_8)
                        val metadata = encryptedPrefsFormat.loadMetadata(content)
                        prefsFiles.add(PrefsFile(file.name, content, metadata))
                    }
                } catch (e: Exception) {
                    aapsLogger.warn(LTag.CORE, "Failed to load cloud file ${file.name}", e)
                    try {
                        val bytes = provider.downloadFile(file.id)
                        if (bytes != null) {
                            val content = String(bytes, Charsets.UTF_8)
                            prefsFiles.add(PrefsFile(file.name, content, emptyMap()))
                        }
                    } catch (e2: Exception) {
                        aapsLogger.error(LTag.CORE, "Failed to download ${file.name}", e2)
                    }
                }
            }

            Pair(prefsFiles, page.nextPageToken)
        }

    override suspend fun getCloudImportFileCount(): Int =
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            val provider = cloudStorageManager.getActiveProvider() ?: return@withContext 0
            provider.countSettingsFiles()
        }

    override fun decryptImportFile(file: PrefsFile, password: String): ImportDecryptResult {
        return try {
            val format: PrefsFormat = encryptedPrefsFormat
            val prefs = format.loadPreferences(file.content, password)
            prefs.metadata = prefFileList.checkMetadata(prefs.metadata)

            val importOk = checkIfImportIsOk(prefs)

            // Check if encryption metadata has ERROR → wrong password
            if (!importOk && prefs.metadata[PrefsMetadataKeyImpl.ENCRYPTION]?.status == PrefsStatusImpl.ERROR) {
                return ImportDecryptResult.WrongPassword
            }

            val importPossible = (importOk || config.isEngineeringMode()) && prefs.values.isNotEmpty()
            ImportDecryptResult.Success(prefs, importOk, importPossible)
        } catch (e: PrefFileNotFoundError) {
            aapsLogger.error(LTag.CORE, "Decrypt failed: file not found", e)
            ImportDecryptResult.Error(e.message ?: "File not found")
        } catch (e: PrefIOError) {
            aapsLogger.error(LTag.CORE, "Decrypt failed: IO error", e)
            ImportDecryptResult.Error(e.message ?: "IO error")
        } catch (e: Exception) {
            aapsLogger.error(LTag.CORE, "Decrypt failed", e)
            ImportDecryptResult.Error(e.message ?: "Unknown error")
        }
    }

    override fun executeImport(prefs: Prefs) {
        activePlugin.beforeImport()
        sp.clear()
        for ((key, value) in prefs.values) {
            if (value == "true" || value == "false") {
                sp.putBoolean(key, value.toBoolean())
            } else {
                sp.putString(key, value)
            }
        }
        activePlugin.afterImport()
    }

    override fun prepareImportRestart() {
        rxBus.send(EventDiaconnG8PumpLogReset())
        preferences.put(BooleanNonKey.GeneralSetupWizardProcessed, true)
    }

    override fun exportUserEntriesCsv(context: Context) {
        aapsLogger.info(LTag.CORE, "${CloudConstants.LOG_PREFIX} CSV_EXPORT exportUserEntriesCsv called, enqueuing WorkManager")
        WorkManager.getInstance(context).enqueueUniqueWork(
            "export",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequest.Builder(CsvExportWorker::class.java).build()
        )
        aapsLogger.info(LTag.CORE, "${CloudConstants.LOG_PREFIX} CSV_EXPORT WorkManager enqueued")
    }

    override suspend fun executeCsvExport(): ExportResult {
        aapsLogger.info(LTag.CORE, "${CloudConstants.LOG_PREFIX} CSV_EXPORT executeCsvExport started")

        val entries = persistenceLayer.getUserEntryFilteredDataFromTime(MidnightTime.calc() - T.days(90).msecs())
        aapsLogger.info(LTag.CORE, "${CloudConstants.LOG_PREFIX} CSV_EXPORT entries count=${entries.size}")

        val csvLocal = sp.getBoolean(ExportPrefKeys.PREF_CSV_LOCAL_ENABLED, true)
        val csvCloud = sp.getBoolean(ExportPrefKeys.PREF_CSV_CLOUD_ENABLED, false)
        val isCloudActive = cloudStorageManager.isCloudStorageActive()
        val cloudEnabled = csvCloud && isCloudActive

        val destination = when {
            csvLocal && cloudEnabled -> ExportDestination.BOTH
            cloudEnabled             -> ExportDestination.CLOUD
            else                     -> ExportDestination.LOCAL
        }
        aapsLogger.info(LTag.CORE, "${CloudConstants.LOG_PREFIX} CSV_EXPORT csvLocal=$csvLocal, csvCloud=$csvCloud, isCloudActive=$isCloudActive, destination=$destination")

        var localSuccess: Boolean? = null
        var cloudSuccess: Boolean? = null

        if (destination == ExportDestination.LOCAL || destination == ExportDestination.BOTH) {
            localSuccess = performLocalCsvExport(entries)
        }

        if (destination == ExportDestination.CLOUD || destination == ExportDestination.BOTH) {
            cloudSuccess = performCloudCsvExport(entries)
        }

        return ExportResult(localSuccess = localSuccess, cloudSuccess = cloudSuccess)
    }

    private fun performLocalCsvExport(userEntries: List<UE>): Boolean {
        return try {
            prefFileList.ensureExportDirExists()
            val newFile = prefFileList.newExportCsvFile() ?: return false
            val contents = userEntryPresentationHelper.userEntriesToCsv(userEntries)
            storage.putFileContents(context.contentResolver, newFile, contents)
            true
        } catch (e: Exception) {
            aapsLogger.error(LTag.CORE, "CSV local export failed", e)
            false
        }
    }

    private suspend fun performCloudCsvExport(userEntries: List<UE>): Boolean {
        return try {
            val provider = cloudStorageManager.getActiveProvider() ?: return false
            val contents = userEntryPresentationHelper.userEntriesToCsv(userEntries)
            val fileName = "UserEntries_${filenameTimestamp()}.csv"
            val folderId = provider.getOrCreateFolderPath(CloudConstants.CLOUD_PATH_USER_ENTRIES)
            folderId?.let { provider.setSelectedFolderId(it) }
            var uploadedFileId = provider.uploadFileToPath(fileName, contents.toByteArray(Charsets.UTF_8), "text/csv", CloudConstants.CLOUD_PATH_USER_ENTRIES)
            if (uploadedFileId == null) {
                uploadedFileId = provider.uploadFile(fileName, contents.toByteArray(Charsets.UTF_8), "text/csv")
            }
            uploadedFileId != null
        } catch (e: Exception) {
            aapsLogger.error(LTag.CORE, "${CloudConstants.LOG_PREFIX} CSV cloud export failed", e)
            false
        }
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
        @Inject lateinit var sp: SP

        override suspend fun doWorkAndLog(): Result {
            aapsLogger.info(LTag.CORE, "${CloudConstants.LOG_PREFIX} CSV_EXPORT doWorkAndLog started")

            val entries = persistenceLayer.getUserEntryFilteredDataFromTime(MidnightTime.calc() - T.days(90).msecs())

            aapsLogger.info(LTag.CORE, "${CloudConstants.LOG_PREFIX} CSV_EXPORT entries count=${entries.size}")

            val csvLocal = sp.getBoolean(ExportPrefKeys.PREF_CSV_LOCAL_ENABLED, true)
            val csvCloud = sp.getBoolean(ExportPrefKeys.PREF_CSV_CLOUD_ENABLED, false)
            val isCloudActive = cloudStorageManager.isCloudStorageActive()
            val cloudEnabled = csvCloud && isCloudActive

            val destination = when {
                csvLocal && cloudEnabled -> ExportDestination.BOTH
                cloudEnabled             -> ExportDestination.CLOUD
                else                     -> ExportDestination.LOCAL
            }
            aapsLogger.info(LTag.CORE, "${CloudConstants.LOG_PREFIX} CSV_EXPORT csvLocal=$csvLocal, csvCloud=$csvCloud, isCloudActive=$isCloudActive, destination=$destination")

            var failed = false

            if (destination == ExportDestination.LOCAL || destination == ExportDestination.BOTH) {
                aapsLogger.info(LTag.CORE, "${CloudConstants.LOG_PREFIX} CSV_EXPORT calling exportToLocal")
                if (exportToLocal(entries) != Result.success()) failed = true
            }

            if (destination == ExportDestination.CLOUD || destination == ExportDestination.BOTH) {
                aapsLogger.info(LTag.CORE, "${CloudConstants.LOG_PREFIX} CSV_EXPORT calling exportToCloud")
                if (exportToCloud(entries) != Result.success()) failed = true
            }

            return if (failed) Result.failure() else Result.success()
        }

        private fun exportToLocal(userEntries: List<UE>): Result {
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
                val fileName = "UserEntries_${filenameTimestamp()}.csv"
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
            val apsResultData = dataWorkerStorage.pickupObject(inputData.getLong(DataWorkerStorage.STORE_KEY, -1)) as? ApsResultData?
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