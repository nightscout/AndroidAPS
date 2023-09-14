package info.nightscout.configuration.maintenance

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.android.HasAndroidInjector
import info.nightscout.configuration.R
import info.nightscout.configuration.activities.DaggerAppCompatActivityWithResult
import info.nightscout.configuration.maintenance.dialogs.PrefImportSummaryDialog
import info.nightscout.configuration.maintenance.formats.EncryptedPrefsFormat
import info.nightscout.core.ui.dialogs.OKDialog
import info.nightscout.core.ui.dialogs.TwoMessagesAlertDialog
import info.nightscout.core.ui.dialogs.WarningDialog
import info.nightscout.core.ui.toast.ToastUtils
import info.nightscout.core.utils.worker.LoggingWorker
import info.nightscout.database.entities.UserEntry
import info.nightscout.database.entities.UserEntry.Action
import info.nightscout.database.entities.UserEntry.Sources
import info.nightscout.interfaces.AndroidPermission
import info.nightscout.interfaces.Config
import info.nightscout.interfaces.db.PersistenceLayer
import info.nightscout.interfaces.logging.UserEntryLogger
import info.nightscout.interfaces.maintenance.ImportExportPrefs
import info.nightscout.interfaces.maintenance.PrefFileListProvider
import info.nightscout.interfaces.maintenance.PrefFileNotFoundError
import info.nightscout.interfaces.maintenance.PrefIOError
import info.nightscout.interfaces.maintenance.PrefMetadata
import info.nightscout.interfaces.maintenance.Prefs
import info.nightscout.interfaces.maintenance.PrefsFile
import info.nightscout.interfaces.maintenance.PrefsFormat
import info.nightscout.interfaces.maintenance.PrefsMetadataKey
import info.nightscout.interfaces.maintenance.PrefsStatus
import info.nightscout.interfaces.protection.PasswordCheck
import info.nightscout.interfaces.storage.Storage
import info.nightscout.interfaces.ui.UiInteraction
import info.nightscout.interfaces.userEntry.UserEntryPresentationHelper
import info.nightscout.interfaces.utils.MidnightTime
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventAppExit
import info.nightscout.rx.events.EventDiaconnG8PumpLogReset
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.rx.weardata.CwfData
import info.nightscout.rx.weardata.CwfMetadataKey
import info.nightscout.rx.weardata.ZipWatchfaceFormat
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.DateUtil
import info.nightscout.shared.utils.T
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.system.exitProcess

/**
 * Created by mike on 03.07.2016.
 */

@Singleton
class ImportExportPrefsImpl @Inject constructor(
    private var log: AAPSLogger,
    private val rh: ResourceHelper,
    private val sp: SP,
    private val config: Config,
    private val rxBus: RxBus,
    private val passwordCheck: PasswordCheck,
    private val androidPermission: AndroidPermission,
    private val encryptedPrefsFormat: EncryptedPrefsFormat,
    private val prefFileList: PrefFileListProvider,
    private val uel: UserEntryLogger,
    private val dateUtil: DateUtil,
    private val uiInteraction: UiInteraction
) : ImportExportPrefs {

    override fun prefsFileExists(): Boolean {
        return prefFileList.listPreferenceFiles().size > 0
    }

    override fun exportSharedPreferences(f: Fragment) {
        f.activity?.let { exportSharedPreferences(it) }
    }

    override fun verifyStoragePermissions(fragment: Fragment, onGranted: Runnable) {
        fragment.context?.let { ctx ->
            val permission = ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            if (permission != PackageManager.PERMISSION_GRANTED) {
                // We don't have permission so prompt the user
                fragment.activity?.let {
                    androidPermission.askForPermission(it, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
                }
            } else onGranted.run()
        }
    }

    private fun prepareMetadata(context: Context): Map<PrefsMetadataKey, PrefMetadata> {

        val metadata: MutableMap<PrefsMetadataKey, PrefMetadata> = mutableMapOf()

        metadata[PrefsMetadataKey.DEVICE_NAME] = PrefMetadata(detectUserName(context), PrefsStatus.OK)
        metadata[PrefsMetadataKey.CREATED_AT] = PrefMetadata(dateUtil.toISOString(dateUtil.now()), PrefsStatus.OK)
        metadata[PrefsMetadataKey.AAPS_VERSION] = PrefMetadata(config.VERSION_NAME, PrefsStatus.OK)
        metadata[PrefsMetadataKey.AAPS_FLAVOUR] = PrefMetadata(config.FLAVOR, PrefsStatus.OK)
        metadata[PrefsMetadataKey.DEVICE_MODEL] = PrefMetadata(config.currentDeviceModelString, PrefsStatus.OK)
        metadata[PrefsMetadataKey.ENCRYPTION] = PrefMetadata("Enabled", PrefsStatus.OK)

        return metadata
    }

    @Suppress("SpellCheckingInspection")
    private fun detectUserName(context: Context): String {
        // based on https://medium.com/@pribble88/how-to-get-an-android-device-nickname-4b4700b3068c
        val n1 = Settings.System.getString(context.contentResolver, "bluetooth_name")
        val n2 = Settings.Secure.getString(context.contentResolver, "bluetooth_name")
        val n3 = try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter?.name
            } else null
        } catch (e: Exception){
            null
        }
        val n4 = Settings.System.getString(context.contentResolver, "device_name")
        val n5 = Settings.Secure.getString(context.contentResolver, "lock_screen_owner_info")
        val n6 = Settings.Global.getString(context.contentResolver, "device_name")

        // name provided (hopefully) by user
        val patientName = sp.getString(info.nightscout.core.utils.R.string.key_patient_name, "")
        val defaultPatientName = rh.gs(info.nightscout.core.ui.R.string.patient_name_default)

        // name we detect from OS
        val systemName = n1 ?: n2 ?: n3 ?: n4 ?: n5 ?: n6 ?: defaultPatientName
        return if (patientName.isNotEmpty() && patientName != defaultPatientName) patientName else systemName
    }

    private fun askForMasterPass(activity: FragmentActivity, @StringRes canceledMsg: Int, then: ((password: String) -> Unit)) {
        passwordCheck.queryPassword(activity, info.nightscout.core.ui.R.string.master_password, info.nightscout.core.utils.R.string.key_master_password, { password ->
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
        passwordCheck.queryAnyPassword(activity, passwordName, info.nightscout.core.utils.R.string.key_master_password, passwordExplanation, passwordWarning, { password ->
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
        if (!sp.contains(info.nightscout.core.utils.R.string.key_master_password) || (sp.getString(info.nightscout.core.utils.R.string.key_master_password, "") == "")) {
            WarningDialog.showWarning(activity,
                                      rh.gs(wrongPwdTitle),
                                      rh.gs(R.string.master_password_missing, rh.gs(R.string.configbuilder_general), rh.gs(R.string.protection)),
                                      R.string.nav_preferences, {
                                          val intent = Intent(activity, uiInteraction.preferencesActivity).apply {
                                              putExtra("id", uiInteraction.prefGeneral)
                                          }
                                          activity.startActivity(intent)
                                      })
            return false
        }
        return true
    }

    private fun askToConfirmExport(activity: FragmentActivity, fileToExport: File, then: ((password: String) -> Unit)) {
        if (!assureMasterPasswordSet(activity, R.string.nav_export)) return

        TwoMessagesAlertDialog.showAlert(
            activity, rh.gs(R.string.nav_export),
            rh.gs(R.string.export_to) + " " + fileToExport.name + " ?",
            rh.gs(R.string.password_preferences_encrypt_prompt), {
                askForMasterPassIfNeeded(activity, R.string.preferences_export_canceled, then)
            }, null, R.drawable.ic_header_export
        )
    }

    private fun askToConfirmImport(activity: FragmentActivity, fileToImport: PrefsFile, then: ((password: String) -> Unit)) {
        if (!assureMasterPasswordSet(activity, R.string.import_setting)) return
        TwoMessagesAlertDialog.showAlert(
            activity, rh.gs(R.string.import_setting),
            rh.gs(R.string.import_from) + " " + fileToImport.name + " ?",
            rh.gs(info.nightscout.core.ui.R.string.password_preferences_decrypt_prompt), {
                askForMasterPass(activity, R.string.preferences_import_canceled, then)
            }, null, R.drawable.ic_header_import
        )
    }

    private fun promptForDecryptionPasswordIfNeeded(
        activity: FragmentActivity, prefs: Prefs, importOk: Boolean,
        format: PrefsFormat, importFile: PrefsFile, then: ((prefs: Prefs, importOk: Boolean) -> Unit)
    ) {

        // current master password was not the one used for decryption, so we prompt for old password...
        if (!importOk && (prefs.metadata[PrefsMetadataKey.ENCRYPTION]?.status == PrefsStatus.ERROR)) {
            askForEncryptionPass(
                activity, R.string.preferences_import_canceled, R.string.old_master_password,
                R.string.different_password_used, R.string.master_password_will_be_replaced
            ) { password ->

                // ...and use it to load & decrypt file again
                val prefsReloaded = format.loadPreferences(importFile.file, password)
                prefsReloaded.metadata = prefFileList.checkMetadata(prefsReloaded.metadata)

                // import is OK when we do not have errors (warnings are allowed)
                val importOkCheckedAgain = checkIfImportIsOk(prefsReloaded)

                then(prefsReloaded, importOkCheckedAgain)
            }
        } else {
            then(prefs, importOk)
        }
    }

    private fun exportSharedPreferences(activity: FragmentActivity) {

        prefFileList.ensureExportDirExists()
        val newFile = prefFileList.newExportFile()

        askToConfirmExport(activity, newFile) { password ->
            try {
                val entries: MutableMap<String, String> = mutableMapOf()
                for ((key, value) in sp.getAll()) {
                    entries[key] = value.toString()
                }

                val prefs = Prefs(entries, prepareMetadata(activity))

                encryptedPrefsFormat.savePreferences(newFile, prefs, password)

                ToastUtils.okToast(activity, rh.gs(R.string.exported))
            } catch (e: FileNotFoundException) {
                ToastUtils.errorToast(activity, rh.gs(R.string.filenotfound) + " " + newFile)
                log.error(LTag.CORE, "Unhandled exception", e)
            } catch (e: IOException) {
                ToastUtils.errorToast(activity, e.message)
                log.error(LTag.CORE, "Unhandled exception", e)
            } catch (e: PrefFileNotFoundError) {
                ToastUtils.Long.errorToast(
                    activity, rh.gs(R.string.preferences_export_canceled)
                        + "\n\n" + rh.gs(R.string.filenotfound)
                        + ": " + e.message
                        + "\n\n" + rh.gs(R.string.need_storage_permission)
                )
                log.error(LTag.CORE, "File system exception", e)
            } catch (e: PrefIOError) {
                ToastUtils.Long.errorToast(
                    activity, rh.gs(R.string.preferences_export_canceled)
                        + "\n\n" + rh.gs(R.string.need_storage_permission)
                        + ": " + e.message
                )
                log.error(LTag.CORE, "File system exception", e)
            }
        }
    }

    override fun importSharedPreferences(fragment: Fragment) {
        fragment.activity?.let { fragmentAct ->
            importSharedPreferences(fragmentAct)
        }
    }

    override fun importSharedPreferences(activity: FragmentActivity) {

        try {
            if (activity is DaggerAppCompatActivityWithResult)
                activity.callForPrefFile.launch(null)
        } catch (e: IllegalArgumentException) {
            // this exception happens on some early implementations of ActivityResult contracts
            // when registered and called for the second time
            ToastUtils.errorToast(activity, rh.gs(R.string.goto_main_try_again))
            log.error(LTag.CORE, "Internal android framework exception", e)
        }
    }

    override fun importCustomWatchface(fragment: Fragment) {
        fragment.activity?.let { importCustomWatchface(it) }
    }
    override fun importCustomWatchface(activity: FragmentActivity) {
        try {
            if (activity is DaggerAppCompatActivityWithResult)
                activity.callForCustomWatchfaceFile.launch(null)
        } catch (e: IllegalArgumentException) {
            // this exception happens on some early implementations of ActivityResult contracts
            // when registered and called for the second time
            ToastUtils.errorToast(activity, rh.gs(R.string.goto_main_try_again))
            log.error(LTag.CORE, "Internal android framework exception", e)
        }
    }

    override fun exportCustomWatchface(customWatchface: CwfData, withDate: Boolean) {
        prefFileList.ensureExportDirExists()
        val newFile = prefFileList.newCwfFile(customWatchface.metadata[CwfMetadataKey.CWF_FILENAME] ?:"", withDate)
        ZipWatchfaceFormat.saveCustomWatchface(newFile, customWatchface)
    }

    override fun importSharedPreferences(activity: FragmentActivity, importFile: PrefsFile) {

        askToConfirmImport(activity, importFile) { password ->

            val format: PrefsFormat = encryptedPrefsFormat

            try {

                val prefsAttempted = format.loadPreferences(importFile.file, password)
                prefsAttempted.metadata = prefFileList.checkMetadata(prefsAttempted.metadata)

                // import is OK when we do not have errors (warnings are allowed)
                val importOkAttempted = checkIfImportIsOk(prefsAttempted)

                promptForDecryptionPasswordIfNeeded(activity, prefsAttempted, importOkAttempted, format, importFile) { prefs, importOk ->

                    // if at end we allow to import preferences
                    val importPossible = (importOk || config.isEngineeringMode()) && (prefs.values.isNotEmpty())

                    PrefImportSummaryDialog.showSummary(activity, importOk, importPossible, prefs, {
                        if (importPossible) {
                            sp.clear()
                            for ((key, value) in prefs.values) {
                                if (value == "true" || value == "false") {
                                    sp.putBoolean(key, value.toBoolean())
                                } else {
                                    sp.putString(key, value)
                                }
                            }

                            restartAppAfterImport(activity)
                        } else {
                            // for impossible imports it should not be called
                            ToastUtils.errorToast(activity, rh.gs(R.string.preferences_import_impossible))
                        }
                    })

                }

            } catch (e: PrefFileNotFoundError) {
                ToastUtils.errorToast(activity, rh.gs(R.string.filenotfound) + " " + importFile)
                log.error(LTag.CORE, "Unhandled exception", e)
            } catch (e: PrefIOError) {
                log.error(LTag.CORE, "Unhandled exception", e)
                ToastUtils.errorToast(activity, e.message)
            }
        }
    }

    private fun checkIfImportIsOk(prefs: Prefs): Boolean {
        var importOk = true

        for ((_, value) in prefs.metadata) {
            if (value.status == PrefsStatus.ERROR)
                importOk = false
        }
        return importOk
    }

    private fun restartAppAfterImport(context: Context) {
        rxBus.send(EventDiaconnG8PumpLogReset())
        sp.putBoolean(R.string.key_setupwizard_processed, true)
        OKDialog.show(context, rh.gs(R.string.setting_imported), rh.gs(R.string.restartingapp)) {
            uel.log(Action.IMPORT_SETTINGS, Sources.Maintenance)
            log.debug(LTag.CORE, "Exiting")
            rxBus.send(EventAppExit())
            if (context is AppCompatActivity) {
                context.finish()
            }
            System.runFinalization()
            exitProcess(0)
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
        context: Context,
        params: WorkerParameters
    ) : LoggingWorker(context, params, Dispatchers.IO) {

        @Inject lateinit var injector: HasAndroidInjector
        @Inject lateinit var rh: ResourceHelper
        @Inject lateinit var prefFileList: PrefFileListProvider
        @Inject lateinit var context: Context
        @Inject lateinit var userEntryPresentationHelper: UserEntryPresentationHelper
        @Inject lateinit var storage: Storage
        @Inject lateinit var persistenceLayer: PersistenceLayer

        override suspend fun doWorkAndLog(): Result {
            val entries = persistenceLayer.getUserEntryFilteredDataFromTime(MidnightTime.calc() - T.days(90).msecs()).blockingGet()
            prefFileList.ensureExportDirExists()
            val newFile = prefFileList.newExportCsvFile()
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

        private fun saveCsv(file: File, userEntries: List<UserEntry>) {
            try {
                val contents = userEntryPresentationHelper.userEntriesToCsv(userEntries)
                storage.putFileContents(file, contents)
            } catch (e: FileNotFoundException) {
                throw PrefFileNotFoundError(file.absolutePath)
            } catch (e: IOException) {
                throw PrefIOError(file.absolutePath)
            }
        }

    }
}