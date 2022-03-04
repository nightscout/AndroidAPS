package info.nightscout.androidaps.plugins.pump.eopatch.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.lifecycle.ViewModelProvider
import info.nightscout.androidaps.activities.NoSplashAppCompatActivity
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.plugins.pump.eopatch.dagger.EopatchPluginQualifier
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import javax.inject.Inject
import io.reactivex.rxkotlin.addTo

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

    override fun toast(message: String) {
        Toast.makeText(this@EoBaseActivity, message, Toast.LENGTH_SHORT).show()
    }

    override fun toast(@StringRes message: Int) {
        Toast.makeText(this@EoBaseActivity, message, Toast.LENGTH_SHORT).show()
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

    fun Disposable.addTo() = addTo(compositeDisposable)
}