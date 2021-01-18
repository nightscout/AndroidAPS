package info.nightscout.androidaps.activities

import dagger.android.support.DaggerAppCompatActivity
import info.nightscout.androidaps.interfaces.ImportExportPrefsInterface
import info.nightscout.androidaps.plugins.general.maintenance.PrefsFileContract
import javax.inject.Inject

open class DaggerAppCompatActivityWithResult : DaggerAppCompatActivity() {

    @Inject lateinit var importExportPrefs: ImportExportPrefsInterface

    val callForPrefFile = registerForActivityResult(PrefsFileContract()) {
        it?.let {
            importExportPrefs.importSharedPreferences(this, it)
        }
    }
}