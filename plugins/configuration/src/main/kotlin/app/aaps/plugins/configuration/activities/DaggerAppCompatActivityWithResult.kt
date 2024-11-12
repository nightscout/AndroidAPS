package app.aaps.plugins.configuration.activities

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import app.aaps.core.interfaces.androidPermissions.AndroidPermission
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.maintenance.ImportExportPrefs
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventAAPSDirectorySelected
import app.aaps.core.interfaces.rx.events.EventThemeSwitch
import app.aaps.core.keys.Preferences
import app.aaps.core.keys.StringKey
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.core.ui.locale.LocaleHelper
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.plugins.configuration.R
import app.aaps.plugins.configuration.maintenance.CustomWatchfaceFileContract
import app.aaps.plugins.configuration.maintenance.PrefsFileContract
import dagger.android.support.DaggerAppCompatActivity
import io.reactivex.rxjava3.disposables.CompositeDisposable
import javax.inject.Inject

open class DaggerAppCompatActivityWithResult : DaggerAppCompatActivity() {

    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var importExportPrefs: ImportExportPrefs
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var androidPermission: AndroidPermission

    private val compositeDisposable = CompositeDisposable()

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        compositeDisposable.add(rxBus.toObservable(EventThemeSwitch::class.java).subscribe {
            recreate()
        })

    }

    override fun onDestroy() {
        compositeDisposable.clear()
        super.onDestroy()
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    val accessTree = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            preferences.put(StringKey.AapsDirectoryUri, uri.toString())
            rxBus.send(EventAAPSDirectorySelected(uri.path ?: "UNKNOWN"))
        }
    }

    val callForPrefFile = registerForActivityResult(PrefsFileContract()) {
        it?.let {
            importExportPrefs.importSharedPreferences(this, it)
        }
    }

    val callForCustomWatchfaceFile = registerForActivityResult(CustomWatchfaceFileContract()) { }

    val callForBatteryOptimization = registerForActivityResult(OptimizationPermissionContract()) {
        updateButtons()
    }

    val requestMultiplePermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        permissions.entries.forEach {
            aapsLogger.info(LTag.CORE, "Permission ${it.key} ${it.value}")
            when (it.key) {
                Manifest.permission.WRITE_EXTERNAL_STORAGE     ->
                    if (it.value && ActivityCompat.checkSelfPermission(this, it.key) == PackageManager.PERMISSION_GRANTED) {
                        //show dialog after permission is granted
                        OKDialog.show(this, "", rh.gs(R.string.alert_dialog_storage_permission_text))
                    }

                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION ->
                if (!it.value || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    androidPermission.notifyForLocationPermissions(this)
                    ToastUtils.errorToast(this, getString(app.aaps.core.ui.R.string.location_permission_not_granted))
                }
                Manifest.permission.ACCESS_BACKGROUND_LOCATION ->
                    if (!it.value || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        androidPermission.notifyForLocationPermissions(this)
                        ToastUtils.errorToast(this, getString(app.aaps.core.ui.R.string.location_permission_not_granted))
                    }
            }
        }
        updateButtons()
    }

    // Used for SetupWizardActivity
    open fun updateButtons() {}
}