package app.aaps.pump.eopatch.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.CallSuper
import androidx.annotation.LayoutRes
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.lifecycle.ViewModelProvider
import app.aaps.pump.eopatch.di.EopatchPluginQualifier
import dagger.android.support.DaggerFragment
import io.reactivex.rxjava3.disposables.CompositeDisposable
import javax.inject.Inject

abstract class EoBaseFragment<B : ViewDataBinding> : DaggerFragment(), EoBaseNavigator {

    @Inject
    @EopatchPluginQualifier
    lateinit var viewModelFactory: ViewModelProvider.Factory

    protected var baseActivity: EoBaseActivity<*>? = null

    protected lateinit var binding: B

    private val compositeDisposable = CompositeDisposable()

    @LayoutRes
    abstract fun getLayoutId(): Int

    @CallSuper
    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is EoBaseActivity<*>) {
            baseActivity = context
        }
    }

    @CallSuper
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DataBindingUtil.inflate(inflater, getLayoutId(), container, false)
        binding.lifecycleOwner = viewLifecycleOwner
        return binding.root
    }

    @CallSuper
    override fun onDestroy() {
        super.onDestroy()
        compositeDisposable.dispose()
    }

    @CallSuper
    override fun onDetach() {
        super.onDetach()
        baseActivity = null
    }

    override fun back() {
        baseActivity?.back()
    }

    override fun finish(finishAffinity: Boolean) {
        baseActivity?.finish(finishAffinity)
    }
}