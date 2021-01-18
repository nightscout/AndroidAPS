package info.nightscout.androidaps.plugins.general.maintenance

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import info.nightscout.androidaps.BuildConfig
import info.nightscout.androidaps.R
import info.nightscout.androidaps.activities.DaggerAppCompatActivityWithResult
import info.nightscout.androidaps.activities.PreferencesActivity
import info.nightscout.androidaps.events.EventAppExit
import info.nightscout.androidaps.interfaces.ConfigInterface
import info.nightscout.androidaps.interfaces.ImportExportPrefsInterface
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.maintenance.formats.*
import info.nightscout.androidaps.utils.AndroidPermission
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.ToastUtils
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.alertDialogs.OKDialog.show
import info.nightscout.androidaps.utils.alertDialogs.PrefImportSummaryDialog
import info.nightscout.androidaps.utils.alertDialogs.TwoMessagesAlertDialog
import info.nightscout.androidaps.utils.alertDialogs.WarningDialog
import info.nightscout.androidaps.utils.buildHelper.BuildHelper
import info.nightscout.androidaps.utils.protection.PasswordCheck
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.system.exitProcess

/**
 * Created by mike on 03.07.2016.
 */

@Singleton
class ImportExportPrefs @Inject constructor(
    private var log: AAPSLogger,
    private val resourceHelper: ResourceHelper,
    private val sp: SP,
    private val buildHelper: BuildHelper,
    private val rxBus: RxBusWrapper,
    private val passwordCheck: PasswordCheck,
    private val config: ConfigInterface,
    private val androidPermission: AndroidPermission,
    private val classicPrefsFormat: ClassicPrefsFormat,
    private val encryptedPrefsFormat: EncryptedPrefsFormat,
    private val prefFileList: PrefFileListProvider
) : ImportExportPrefsInterface {

    override fun prefsFileExists(): Boolean {
        return prefFileList.listPreferenceFiles().size > 0
    }

    override fun exportSharedPreferences(f: Fragment) {
        f.activity?.let { exportSharedPreferences(it) }
    }

    override fun verifyStoragePermissions(fragment: Fragment, onGranted: Runnable) {
        fragment.context?.let { ctx ->
            val permission = ContextCompat.checkSelfPermission(ctx,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
            if (permission != PackageManager.PERMISSION_GRANTED) {
                // We don't have permission so prompt the user
                fragment.activity?.let {
                    androidPermission.askForPermission(it,
                        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
                }
            } else {
                onGranted.run()
            }
        }
    }

    private fun prepareMetadata(context: Context): Map<PrefsMetadataKey, PrefMetadata> {

        val metadata: MutableMap<PrefsMetadataKey, PrefMetadata> = mutableMapOf()

        metadata[PrefsMetadataKey.DEVICE_NAME] = PrefMetadata(detectUserName(context), PrefsStatus.OK)
        metadata[PrefsMetadataKey.CREATED_AT] = PrefMetadata(DateUtil.toISOString(Date()), PrefsStatus.OK)
        metadata[PrefsMetadataKey.AAPS_VERSION] = PrefMetadata(BuildConfig.VERSION_NAME, PrefsStatus.OK)
        metadata[PrefsMetadataKey.AAPS_FLAVOUR] = PrefMetadata(BuildConfig.FLAVOR, PrefsStatus.OK)
        metadata[PrefsMetadataKey.DEVICE_MODEL] = PrefMetadata(config.currentDeviceModelString, PrefsStatus.OK)

        if (prefsEncryptionIsDisabled()) {
            metadata[PrefsMetadataKey.ENCRYPTION] = PrefMetadata("Disabled", PrefsStatus.DISABLED)
        } else {
            metadata[PrefsMetadataKey.ENCRYPTION] = PrefMetadata("Enabled", PrefsStatus.OK)
        }

        return metadata
    }

    private fun detectUserName(context: Context): String {
        // based on https://medium.com/@pribble88/how-to-get-an-android-device-nickname-4b4700b3068c
        val n1 = Settings.System.getString(context.contentResolver, "bluetooth_name")
        val n2 = Settings.Secure.getString(context.contentResolver, "bluetooth_name")
        val n3 = BluetoothAdapter.getDefaultAdapter()?.name
        val n4 = Settings.System.getString(context.contentResolver, "device_name")
        val n5 = Settings.Secure.getString(context.contentResolver, "lock_screen_owner_info")
        val n6 = Settings.Global.getString(context.contentResolver, "device_name")

        // name provided (hopefully) by user
        val patientName = sp.getString(R.string.key_patient_name, "")
        val defaultPatientName = resourceHelper.gs(R.string.patient_name_default)

        // name we detect from OS
        val systemName = n1 ?: n2 ?: n3 ?: n4 ?: n5 ?: n6 ?: defaultPatientName
        return if (patientName.isNotEmpty() && patientName != defaultPatientName) patientName else systemName
    }

    private fun prefsEncryptionIsDisabled() =
        buildHelper.isEngineeringMode() && !sp.getBoolean(R.string.key_maintenance_encrypt_exported_prefs, true)

    private fun askForMasterPass(activity: FragmentActivity, @StringRes canceledMsg: Int, then: ((password: String) -> Unit)) {
        passwordCheck.queryPassword(activity, R.string.master_password, R.string.key_master_password, { password ->
            then(password)
        }, {
            ToastUtils.warnToast(activity, resourceHelper.gs(canceledMsg))
        })
    }

    @Suppress("SameParameterValue")
    private fun askForEncryptionPass(activity: FragmentActivity, @StringRes canceledMsg: Int, @StringRes passwordName: Int, @StringRes passwordExplanation: Int?,
                                     @StringRes passwordWarning: Int?, then: ((password: String) -> Unit)) {
        passwordCheck.queryAnyPassword(activity, passwordName, R.string.key_master_password, passwordExplanation, passwordWarning, { password ->
            then(password)
        }, {
            ToastUtils.warnToast(activity, resourceHelper.gs(canceledMsg))
        })
    }

    @Suppress("SameParameterValue")
    private fun askForMasterPassIfNeeded(activity: FragmentActivity, @StringRes canceledMsg: Int, then: ((password: String) -> Unit)) {
        if (prefsEncryptionIsDisabled()) {
            then("")
        } else {
            askForMasterPass(activity, canceledMsg, then)
        }
    }

    private fun assureMasterPasswordSet(activity: FragmentActivity, @StringRes wrongPwdTitle: Int): Boolean {
        if (!sp.contains(R.string.key_master_password) || (sp.getString(R.string.key_master_password, "") == "")) {
            WarningDialog.showWarning(activity,
                resourceHelper.gs(wrongPwdTitle),
                resourceHelper.gs(R.string.master_password_missing, resourceHelper.gs(R.string.configbuilder_general), resourceHelper.gs(R.string.protection)),
                R.string.nav_preferences, {
                val intent = Intent(activity, PreferencesActivity::class.java).apply {
                    putExtra("id", R.xml.pref_general)
                }
                activity.startActivity(intent)
            })
            return false
        }
        return true
    }

    private fun askToConfirmExport(activity: FragmentActivity, fileToExport: File, then: ((password: String) -> Unit)) {
        if (!prefsEncryptionIsDisabled() && !assureMasterPasswordSet(activity, R.string.nav_export)) return

        TwoMessagesAlertDialog.showAlert(activity, resourceHelper.gs(R.string.nav_export),
            resourceHelper.gs(R.string.export_to) + " " + fileToExport.name + " ?",
            resourceHelper.gs(R.string.password_preferences_encrypt_prompt), {
            askForMasterPassIfNeeded(activity, R.string.preferences_export_canceled, then)
        }, null, R.drawable.ic_header_export)
    }

    private fun askToConfirmImport(activity: FragmentActivity, fileToImport: PrefsFile, then: ((password: String) -> Unit)) {

        if (fileToImport.handler == PrefsFormatsHandler.ENCRYPTED) {
            if (!assureMasterPasswordSet(activity, R.string.nav_import)) return
            TwoMessagesAlertDialog.showAlert(activity, resourceHelper.gs(R.string.nav_import),
                resourceHelper.gs(R.string.import_from) + " " + fileToImport.name + " ?",
                resourceHelper.gs(R.string.password_preferences_decrypt_prompt), {
                askForMasterPass(activity, R.string.preferences_import_canceled, then)
            }, null, R.drawable.ic_header_import)

        } else {
            OKDialog.showConfirmation(activity, resourceHelper.gs(R.string.nav_import),
                resourceHelper.gs(R.string.import_from) + " " + fileToImport.file + " ?",
                Runnable { then("") })
        }
    }

    private fun promptForDecryptionPasswordIfNeeded(activity: FragmentActivity, prefs: Prefs, importOk: Boolean,
                                                    format: PrefsFormat, importFile: PrefsFile, then: ((prefs: Prefs, importOk: Boolean) -> Unit)) {

        // current master password was not the one used for decryption, so we prompt for old password...
        if (!importOk && (prefs.metadata[PrefsMetadataKey.ENCRYPTION]?.status == PrefsStatus.ERROR)) {
            askForEncryptionPass(activity, R.string.preferences_import_canceled, R.string.old_master_password,
                R.string.different_password_used, R.string.master_password_will_be_replaced) { password ->

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
        val legacyFile = prefFileList.legacyFile()
        val newFile = prefFileList.newExportFile()

        askToConfirmExport(activity, newFile) { password ->
            try {
                val entries: MutableMap<String, String> = mutableMapOf()
                for ((key, value) in sp.getAll()) {
                    entries[key] = value.toString()
                }

                val prefs = Prefs(entries, prepareMetadata(activity))

                if (BuildConfig.DEBUG && buildHelper.isEngineeringMode()) {
                    classicPrefsFormat.savePreferences(legacyFile, prefs)
                }
                encryptedPrefsFormat.savePreferences(newFile, prefs, password)

                ToastUtils.okToast(activity, resourceHelper.gs(R.string.exported))
            } catch (e: FileNotFoundException) {
                ToastUtils.errorToast(activity, resourceHelper.gs(R.string.filenotfound) + " " + newFile)
                log.error(LTag.CORE, "Unhandled exception", e)
            } catch (e: IOException) {
                ToastUtils.errorToast(activity, e.message)
                log.error(LTag.CORE, "Unhandled exception", e)
            } catch (e: PrefFileNotFoundError) {
                ToastUtils.Long.errorToast(activity, resourceHelper.gs(R.string.preferences_export_canceled)
                    + "\n\n" + resourceHelper.gs(R.string.filenotfound)
                    + ": " + e.message
                    + "\n\n" + resourceHelper.gs(R.string.needstoragepermission))
                log.error(LTag.CORE, "File system exception", e)
            } catch (e: PrefIOError) {
                ToastUtils.Long.errorToast(activity, resourceHelper.gs(R.string.preferences_export_canceled)
                    + "\n\n" + resourceHelper.gs(R.string.needstoragepermission)
                    + ": " + e.message)
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
            ToastUtils.errorToast(activity, resourceHelper.gs(R.string.goto_main_try_again))
            log.error(LTag.CORE, "Internal android framework exception", e)
        }
    }

    override fun importSharedPreferences(activity: FragmentActivity, importFile: PrefsFile) {

        askToConfirmImport(activity, importFile) { password ->

            val format: PrefsFormat = when (importFile.handler) {
                PrefsFormatsHandler.CLASSIC -> classicPrefsFormat
                PrefsFormatsHandler.ENCRYPTED -> encryptedPrefsFormat
            }

            try {

                val prefsAttempted = format.loadPreferences(importFile.file, password)
                prefsAttempted.metadata = prefFileList.checkMetadata(prefsAttempted.metadata)

                // import is OK when we do not have errors (warnings are allowed)
                val importOkAttempted = checkIfImportIsOk(prefsAttempted)

                promptForDecryptionPasswordIfNeeded(activity, prefsAttempted, importOkAttempted, format, importFile) { prefs, importOk ->

                    // if at end we allow to import preferences
                    val importPossible = (importOk || buildHelper.isEngineeringMode()) && (prefs.values.isNotEmpty())

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
                            ToastUtils.errorToast(activity, resourceHelper.gs(R.string.preferences_import_impossible))
                        }
                    })

                }

            } catch (e: PrefFileNotFoundError) {
                ToastUtils.errorToast(activity, resourceHelper.gs(R.string.filenotfound) + " " + importFile)
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
        sp.putBoolean(R.string.key_setupwizard_processed, true)
        show(context, resourceHelper.gs(R.string.setting_imported), resourceHelper.gs(R.string.restartingapp), Runnable {
            log.debug(LTag.CORE, "Exiting")
            rxBus.send(EventAppExit())
            if (context is AppCompatActivity) {
                context.finish()
            }
            System.runFinalization()
            exitProcess(0)
        })
    }
}