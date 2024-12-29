package app.aaps.pump.eopatch.ui

import android.os.Bundle
import androidx.annotation.LayoutRes
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.lifecycle.ViewModelProvider
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.ui.activities.TranslatedDaggerAppCompatActivity
import app.aaps.pump.eopatch.di.EopatchPluginQualifier
import javax.inject.Inject

abstract class EoBaseActivity<B : ViewDataBinding> : TranslatedDaggerAppCompatActivity(), EoBaseNavigator {

    @EopatchPluginQualifier
    @Inject lateinit var viewModelFactory: ViewModelProvider.Factory
    @Inject lateinit var aapsSchedulers: AapsSchedulers

    protected lateinit var binding: B

    @LayoutRes abstract fun getLayoutId(): Int

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