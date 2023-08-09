package info.nightscout.pump.medtrum.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.ActivityResultLauncher
import androidx.annotation.LayoutRes
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.lifecycle.ViewModelProvider
import dagger.android.support.DaggerAppCompatActivity
import info.nightscout.core.ui.R
import info.nightscout.pump.medtrum.di.MedtrumPluginQualifier
import info.nightscout.rx.AapsSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import javax.inject.Inject

abstract class MedtrumBaseActivity<B : ViewDataBinding> : DaggerAppCompatActivity(), MedtrumBaseNavigator {
    @Inject
    @MedtrumPluginQualifier
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
