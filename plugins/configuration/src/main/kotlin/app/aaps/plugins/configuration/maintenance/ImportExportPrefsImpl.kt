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
import app.aaps.shared.impl.weardata.ZipWatchfaceFormat
import dagger.Reusable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import kotlinx.coroutines.Dispatchers
import org.json.JSONObject
import java.io.FileNotFoundException
import java.io.IOException
import javax.inject.Inject

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
    private val configBuilder: ConfigBuilder
) : ImportExportPrefs {

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
        prefFileList.ensureExportDirExists()
        val newFile = prefFileList.newPreferenceFile() ?: return

        askToConfirmExport(activity, newFile) { password ->
            // Save preferences
            val exportResultMessage = if (savePreferences(newFile, password))
                rh.gs(R.string.exported)
            else
                rh.gs(R.string.exported_failed)

            // Send toast alert to overview
            ToastUtils.okToast(activity, exportResultMessage)

            // Register this event
            disposable += persistenceLayer.insertPumpTherapyEventIfNewByTimestamp(
                therapyEvent = TE.asSettingsExport(error = exportResultMessage),
                timestamp = dateUtil.now(),
                action = Action.EXPORT_SETTINGS, // Signal export was done....
                source = Sources.Automation,
                note = "Manual: $exportResultMessage",
                listValues = listOf()
            ).subscribe()
        }
    }

    override fun exportSharedPreferencesNonInteractive(context: Context, password: String): Boolean {
        prefFileList.ensureExportDirExists()
        val newFile = prefFileList.newPreferenceFile() ?: return false

        // Registering export settings event already done by automation
        return savePreferences(newFile, password)
    }

    override fun importSharedPreferences(activity: FragmentActivity) {

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
        WorkManager.getInstance(activity).enqueueUniqueWork(
            "export",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequest.Builder(CsvExportWorker::class.java).build()
        )
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

        override suspend fun doWorkAndLog(): Result {
            val entries = persistenceLayer.getUserEntryFilteredDataFromTime(MidnightTime.calc() - T.days(90).msecs()).blockingGet()
            prefFileList.ensureExportDirExists()
            val newFile = prefFileList.newExportCsvFile() ?: return Result.failure()
            var ret = Result.success()
            try {
                saveCsv(newFile, entries)
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