package info.nightscout.androidaps.plugins.pump.eopatch.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.ActivityResultLauncher
import androidx.annotation.LayoutRes
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.lifecycle.ViewModelProvider
import info.nightscout.androidaps.activities.NoSplashAppCompatActivity
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.plugins.pump.eopatch.dagger.EopatchPluginQualifier
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import javax.inject.Inject

abstract class EoBaseActivity<B : ViewDataBinding> : NoSplashAppCompatActivity(), EoBaseNavigator {
    @Inject
    @EopatchPluginQualifier
    lateinit var viewModelFactory: ViewModelProvider.Factory

    @Inject lateinit var aapsSchedulers: AapsSchedulers

    protected lateinit var binding: B

    private val compositeDisposable = CompositeDisposable()

    protected lateinit var getResult: ActivityResultLauncher<Intent>

    @LayoutRes
    abstract fun getLayoutId(): Int

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.AppTheme_NoActionBar)

        binding = DataBindingUtil.setContentView(this, getLayoutId())
        binding.lifecycleOwner = this

    }

    override fun back() {
        if(supportFragmentManager.backStackEntryCount == 0) {
            finish()
        } else {
            supportFragmentManager.popBackStack()
        }
    }

    override fun finish(finishAffinity: Boolean) {
        if(finishAffinity) {
            finishAffinity()
        } else {
            finish()
        }
    }
}