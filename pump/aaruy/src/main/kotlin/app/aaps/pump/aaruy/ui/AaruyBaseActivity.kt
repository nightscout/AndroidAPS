package app.aaps.pump.aaruy.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.ActivityResultLauncher
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.lifecycle.ViewModelProvider
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.ui.activities.TranslatedDaggerAppCompatActivity
import app.aaps.pump.aaruy.di.AaruyPluginQualifier
import io.reactivex.rxjava3.disposables.CompositeDisposable
import javax.inject.Inject

abstract class AaruyBaseActivity<B : ViewDataBinding> : TranslatedDaggerAppCompatActivity(), AaruyBaseNavigator {

    @Inject
    @AaruyPluginQualifier
    lateinit var viewModelFactory: ViewModelProvider.Factory

    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var context: Context
    @Inject lateinit var aapsLogger: AAPSLogger

    protected lateinit var binding: B

    private val compositeDisposable = CompositeDisposable()

    protected lateinit var getResult: ActivityResultLauncher<Intent>
    protected var activity: AppCompatActivity? = null

    @LayoutRes
    abstract fun getLayoutId(): Int

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = DataBindingUtil.setContentView(this, getLayoutId())
        binding.lifecycleOwner = this

    }

    override fun back() {
        if (supportFragmentManager.backStackEntryCount == 0) {
            finish()
        } else {
            supportFragmentManager.popBackStack()
        }
    }

    override fun finish(finishAffinity: Boolean) {
        if (finishAffinity) {
            finishAffinity()
        } else {
            finish()
        }
    }
}
