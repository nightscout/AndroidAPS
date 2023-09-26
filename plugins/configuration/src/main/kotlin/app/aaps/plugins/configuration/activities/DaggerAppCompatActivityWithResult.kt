package app.aaps.plugins.configuration.activities

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.maintenance.ImportExportPrefs
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventThemeSwitch
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.core.ui.locale.LocaleHelper
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
            if (it.value)
                if (ActivityCompat.checkSelfPermission(this, it.key) == PackageManager.PERMISSION_GRANTED) {
                    when (it.key) {
                        Manifest.permission.WRITE_EXTERNAL_STORAGE ->
                            //show dialog after permission is granted
                            OKDialog.show(this, "", rh.gs(R.string.alert_dialog_storage_permission_text))
                        //  ignore the rest
                    }
                }
        }
        updateButtons()
    }

    // Used for SetupWizardActivity
    open fun updateButtons() {}
}