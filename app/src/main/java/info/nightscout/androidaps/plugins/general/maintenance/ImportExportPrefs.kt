package info.nightscout.androidaps.plugins.general.maintenance

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import info.nightscout.androidaps.BuildConfig
import info.nightscout.androidaps.R
import info.nightscout.androidaps.activities.PreferencesActivity
import info.nightscout.androidaps.events.EventAppExit
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.maintenance.formats.*
import info.nightscout.androidaps.plugins.general.smsCommunicator.otp.OneTimePassword
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.alertDialogs.OKDialog.show
import info.nightscout.androidaps.utils.ToastUtils
import info.nightscout.androidaps.utils.alertDialogs.PrefImportSummaryDialog
import info.nightscout.androidaps.utils.alertDialogs.TwoMessagesAlertDialog
import info.nightscout.androidaps.utils.alertDialogs.WarningDialog
import info.nightscout.androidaps.utils.buildHelper.BuildHelper
import info.nightscout.androidaps.utils.protection.PasswordCheck
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import org.joda.time.DateTime
import org.joda.time.Days
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Created by mike on 03.07.2016.
 */

private const val REQUEST_EXTERNAL_STORAGE = 1
private val PERMISSIONS_STORAGE = arrayOf(
    Manifest.permission.READ_EXTERNAL_STORAGE,
    Manifest.permission.WRITE_EXTERNAL_STORAGE
)

private const val IMPORT_AGE_NOT_YET_OLD_DAYS = 60

@Singleton
class ImportExportPrefs @Inject constructor(
    private var log: AAPSLogger,
    private val resourceHelper: ResourceHelper,
    private val sp: SP,
    private val buildHelper: BuildHelper,
    private val otp: OneTimePassword,
    private val rxBus: RxBusWrapper,
    private val passwordCheck: PasswordCheck,
    private val classicPrefsFormat: ClassicPrefsFormat,
    private val encryptedPrefsFormat: EncryptedPrefsFormat
) {

    val TAG = LTag.CORE

    private val path = File(Environment.getExternalStorageDirectory().toString())

    private val file = File(path, resourceHelper.gs(R.string.app_name) + "Preferences")
    private val encFile = File(path, resourceHelper.gs(R.string.app_name) + "Preferences.json")

    fun prefsImportFile(): File {
        return if (encFile.exists()) encFile else file
    }

    fun prefsFileExists(): Boolean {
        return encFile.exists() || file.exists()
    }


    fun exportSharedPreferences(f: Fragment) {
        f.activity?.let { exportSharedPreferences(it) }
    }

    fun verifyStoragePermissions(fragment: Fragment) {
        fragment.context?.let {
            val permission = ContextCompat.checkSelfPermission(it,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
            if (permission != PackageManager.PERMISSION_GRANTED) {
                // We don't have permission so prompt the user
                fragment.requestPermissions(PERMISSIONS_STORAGE, REQUEST_EXTERNAL_STORAGE)
            }
        }
    }

    private fun prepareMetadata(context: Context): Map<PrefsMetadataKey, PrefMetadata> {

        val metadata: MutableMap<PrefsMetadataKey, PrefMetadata> = mutableMapOf()

        metadata[PrefsMetadataKey.DEVICE_NAME] = PrefMetadata(detectUserName(context), PrefsStatus.OK)
        metadata[PrefsMetadataKey.CREATED_AT] = PrefMetadata(DateUtil.toISOString(Date()), PrefsStatus.OK)
        metadata[PrefsMetadataKey.AAPS_VERSION] = PrefMetadata(BuildConfig.VERSION_NAME, PrefsStatus.OK)
        metadata[PrefsMetadataKey.AAPS_FLAVOUR] = PrefMetadata(BuildConfig.FLAVOR, PrefsStatus.OK)
        metadata[PrefsMetadataKey.DEVICE_MODEL] = PrefMetadata(getCurrentDeviceModelString(), PrefsStatus.OK)

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
        val name = if (patientName.isNotEmpty() && patientName != defaultPatientName) patientName else systemName
        return name
    }

    private fun getCurrentDeviceModelString() =
        Build.MANUFACTURER + " " + Build.MODEL + " (" + Build.DEVICE + ")"

    private fun prefsEncryptionIsDisabled() =
        buildHelper.isEngineeringMode() && !sp.getBoolean(resourceHelper.gs(R.string.key_maintenance_encrypt_exported_prefs), true)

    private fun askForMasterPass(activity: Activity, @StringRes canceledMsg: Int, then: ((password: String) -> Unit)) {
        passwordCheck.queryPassword(activity, R.string.master_password, R.string.key_master_password, { password ->
            then(password)
        }, {
            ToastUtils.warnToast(activity, resourceHelper.gs(canceledMsg))
        })
    }

    private fun askForMasterPassIfNeeded(activity: Activity, @StringRes canceledMsg: Int, then: ((password: String) -> Unit)) {
        if (prefsEncryptionIsDisabled()) {
            then("")
        } else {
            askForMasterPass(activity, canceledMsg, then)
        }
    }

    private fun assureMasterPasswordSet(activity: Activity, @StringRes wrongPwdTitle: Int): Boolean {
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

    private fun askToConfirmExport(activity: Activity, then: ((password: String) -> Unit)) {
        if (!prefsEncryptionIsDisabled() && !assureMasterPasswordSet(activity, R.string.nav_export)) return

        TwoMessagesAlertDialog.showAlert(activity, resourceHelper.gs(R.string.nav_export),
            resourceHelper.gs(R.string.export_to) + " " + encFile + " ?",
            resourceHelper.gs(R.string.password_preferences_encrypt_prompt), {
            askForMasterPassIfNeeded(activity, R.string.preferences_export_canceled, then)
        }, null,  R.drawable.ic_header_export)
    }

    private fun askToConfirmImport(activity: Activity, fileToImport: File, then: ((password: String) -> Unit)) {

        if (encFile.exists()) {
            if (!assureMasterPasswordSet(activity, R.string.nav_import)) return

            TwoMessagesAlertDialog.showAlert(activity, resourceHelper.gs(R.string.nav_import),
                resourceHelper.gs(R.string.import_from) + " " + fileToImport + " ?",
                resourceHelper.gs(R.string.password_preferences_decrypt_prompt), {
                askForMasterPass(activity, R.string.preferences_import_canceled, then)
            }, null, R.drawable.ic_header_import)

        } else {
            OKDialog.showConfirmation(activity, resourceHelper.gs(R.string.nav_import),
                resourceHelper.gs(R.string.import_from) + " " + fileToImport + " ?",
                Runnable { then("") })
        }
    }

    private fun exportSharedPreferences(activity: Activity) {
        askToConfirmExport(activity) { password ->
            try {
                val entries: MutableMap<String, String> = mutableMapOf()
                for ((key, value) in sp.getAll()) {
                    entries[key] = value.toString()
                }

                val prefs = Prefs(entries, prepareMetadata(activity))

                classicPrefsFormat.savePreferences(file, prefs)
                encryptedPrefsFormat.savePreferences(encFile, prefs, password)

                ToastUtils.okToast(activity, resourceHelper.gs(R.string.exported))
            } catch (e: FileNotFoundException) {
                ToastUtils.errorToast(activity, resourceHelper.gs(R.string.filenotfound) + " " + encFile)
                log.error(TAG, "Unhandled exception", e)
            } catch (e: IOException) {
                ToastUtils.errorToast(activity, e.message)
                log.error(TAG, "Unhandled exception", e)
            }
        }
    }

    fun importSharedPreferences(fragment: Fragment) {
        fragment.activity?.let { importSharedPreferences(it) }
    }

    fun importSharedPreferences(activity: Activity) {

        val importFile = prefsImportFile()

        askToConfirmImport(activity, importFile) { password ->

            val format: PrefsFormat = if (encFile.exists()) encryptedPrefsFormat else classicPrefsFormat

            try {

                val prefs = format.loadPreferences(importFile, password)
                prefs.metadata = checkMetadata(prefs.metadata)

                // import is OK when we do not have errors (warnings are allowed)
                val importOk = checkIfImportIsOk(prefs)

                // if at end we allow to import preferences
                val importPossible = (importOk || buildHelper.isEngineeringMode()) && (prefs.values.size > 0)

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
                        ToastUtils.errorToast(activity, "Cannot import preferences!")
                    }
                })

            } catch (e: PrefFileNotFoundError) {
                ToastUtils.errorToast(activity, resourceHelper.gs(R.string.filenotfound) + " " + importFile)
                log.error(TAG, "Unhandled exception", e)
            } catch (e: PrefIOError) {
                log.error(TAG, "Unhandled exception", e)
                ToastUtils.errorToast(activity, e.message)
            }
        }
    }

    // check metadata for known issues, change their status and add info with explanations
    private fun checkMetadata(metadata: Map<PrefsMetadataKey, PrefMetadata>): Map<PrefsMetadataKey, PrefMetadata> {
        val meta = metadata.toMutableMap()

         meta[PrefsMetadataKey.AAPS_FLAVOUR]?.let { flavour ->
             val flavourOfPrefs = flavour.value
             if (flavour.value != BuildConfig.FLAVOR) {
                 flavour.status = PrefsStatus.WARN
                 flavour.info = resourceHelper.gs(R.string.metadata_warning_different_flavour, flavourOfPrefs, BuildConfig.FLAVOR)
             }
         }

        meta[PrefsMetadataKey.DEVICE_MODEL]?.let { model ->
            if (model.value != getCurrentDeviceModelString()) {
                model.status = PrefsStatus.WARN
                model.info = resourceHelper.gs(R.string.metadata_warning_different_device)
            }
        }

        meta[PrefsMetadataKey.CREATED_AT]?.let { createdAt ->
            try {
                val date1 = DateTime.parse(createdAt.value);
                val date2 = DateTime.now()

                val daysOld = Days.daysBetween(date1.toLocalDate(), date2.toLocalDate()).getDays()

                if (daysOld > IMPORT_AGE_NOT_YET_OLD_DAYS) {
                    createdAt.status = PrefsStatus.WARN
                    createdAt.info = resourceHelper.gs(R.string.metadata_warning_old_export, daysOld.toString())
                }
            } catch (e: Exception) {
                createdAt.status = PrefsStatus.WARN
                createdAt.info = resourceHelper.gs(R.string.metadata_warning_date_format)
            }
        }

        return meta
    }

    private fun checkIfImportIsOk(prefs: Prefs): Boolean {
        var importOk = true

        for ((_, value) in prefs.metadata) {
            if (value.status == PrefsStatus.ERROR)
                importOk = false;
        }
        return importOk
    }

    private fun restartAppAfterImport(context: Context) {
        sp.putBoolean(R.string.key_setupwizard_processed, true)
        show(context, resourceHelper.gs(R.string.setting_imported), resourceHelper.gs(R.string.restartingapp), Runnable {
            log.debug(TAG, "Exiting")
            rxBus.send(EventAppExit())
            if (context is Activity) {
                context.finish()
            }
            System.runFinalization()
            System.exit(0)
        })
    }
}