package app.aaps.core.interfaces.maintenance

import android.content.Context
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import app.aaps.core.interfaces.rx.weardata.CwfData
import org.json.JSONObject

interface ImportExportPrefs {

    fun importSharedPreferences(activity: FragmentActivity, importFile: PrefsFile)
    fun importSharedPreferences(activity: FragmentActivity)
    fun importSharedPreferences(fragment: Fragment)
    fun importCustomWatchface(activity: FragmentActivity)
    fun importCustomWatchface(fragment: Fragment)
    fun exportCustomWatchface(customWatchface: CwfData, withDate: Boolean = true)
    fun prefsFileExists(): Boolean
    fun verifyStoragePermissions(fragment: Fragment, onGranted: Runnable)
    fun exportSharedPreferences(f: Fragment)
    fun exportSharedPreferencesNonInteractive(context: Context, password: String): Boolean
    fun exportUserEntriesCsv(activity: FragmentActivity)
    fun exportApsResult(algorithm: String?, input: JSONObject, output: JSONObject?)
}