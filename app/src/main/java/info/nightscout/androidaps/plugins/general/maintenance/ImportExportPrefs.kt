package info.nightscout.androidaps.plugins.general.maintenance

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import info.nightscout.androidaps.R
import info.nightscout.androidaps.events.EventAppExit
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.maintenance.formats.*
import info.nightscout.androidaps.utils.OKDialog.show
import info.nightscout.androidaps.utils.OKDialog.showConfirmation
import info.nightscout.androidaps.utils.ToastUtils
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
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

@Singleton
class ImportExportPrefs @Inject constructor (
    private var log: AAPSLogger,
    private val resourceHelper: ResourceHelper,
    private val sp : SP,
    private val rxBus: RxBusWrapper
)
{

    val TAG = LTag.CORE

    private val path = File(Environment.getExternalStorageDirectory().toString())

    private val file = File(path, resourceHelper.gs(R.string.app_name) + "Preferences")
    private val encFile = File(path, resourceHelper.gs(R.string.app_name) + "Preferences.json")

    fun prefsImportFile() : File {
        return if (encFile.exists()) encFile else file
    }

    fun prefsFileExists() : Boolean {
        return encFile.exists() || file.exists()
    }

    fun exportSharedPreferences(f: Fragment) {
        exportSharedPreferences(f.context)
    }

    fun verifyStoragePermissions(fragment: Fragment) {
        val permission = ContextCompat.checkSelfPermission(fragment.context!!,
            Manifest.permission.WRITE_EXTERNAL_STORAGE)
        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            fragment.requestPermissions(PERMISSIONS_STORAGE, REQUEST_EXTERNAL_STORAGE)
        }
    }

    private fun exportSharedPreferences(context: Context?) {
        showConfirmation(context!!, resourceHelper.gs(R.string.maintenance), resourceHelper.gs(R.string.export_to) + " " + encFile + " ?", Runnable {
            try {
                val entries: MutableMap<String, String> = mutableMapOf()
                for ((key, value) in sp.getAll()) {
                    entries[key] = value.toString()
                }

                val prefs =  Prefs(entries, mapOf())

                ClassicPrefsFormat.savePreferences(file, prefs)
                EncryptedPrefsFormat.savePreferences(encFile, prefs)

                ToastUtils.showToastInUiThread(context, resourceHelper.gs(R.string.exported))
            } catch (e: FileNotFoundException) {
                ToastUtils.showToastInUiThread(context, resourceHelper.gs(R.string.filenotfound) + " " + encFile)
                log.error(TAG,"Unhandled exception", e)
            } catch (e: IOException) {
                log.error(TAG,"Unhandled exception", e)
            }
        })
    }

    fun importSharedPreferences(fragment: Fragment) {
        importSharedPreferences(fragment.context)
    }

    fun importSharedPreferences(context: Context?) {

        val importFile = prefsImportFile()

        showConfirmation(context!!, resourceHelper.gs(R.string.maintenance), resourceHelper.gs(R.string.import_from) + " " + importFile + " ?", Runnable {

            val format : PrefsFormat = if (encFile.exists()) EncryptedPrefsFormat else ClassicPrefsFormat

            try {
                val prefs = format.loadPreferences(importFile)

                sp.clear()
                for ((key, value) in prefs.values) {
                    if (value == "true" || value == "false") {
                        sp.putBoolean(key, value.toBoolean())
                    } else {
                        sp.putString(key, value)
                    }
                }

                sp.putBoolean(R.string.key_setupwizard_processed, true)
                show(context, resourceHelper.gs(R.string.setting_imported), resourceHelper.gs(R.string.restartingapp), Runnable {
                    log.debug(TAG,"Exiting")
                    rxBus.send(EventAppExit())
                    if (context is Activity) {
                        context.finish()
                    }
                    System.runFinalization()
                    System.exit(0)
                })


            } catch (e: PrefFileNotFoundError) {
                ToastUtils.showToastInUiThread(context, resourceHelper.gs(R.string.filenotfound) + " " + importFile)
                log.error(TAG,"Unhandled exception", e)
            } catch (e: PrefIOError) {
                log.error(TAG,"Unhandled exception", e)
            }

        })
    }
}