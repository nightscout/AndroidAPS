package app.aaps.pump.aaruy.ui

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
import dagger.android.support.DaggerFragment
import app.aaps.pump.aaruy.di.AaruyPluginQualifier
import io.reactivex.rxjava3.disposables.CompositeDisposable
import javax.inject.Inject

abstract class AaruyBaseFragment<B : ViewDataBinding> : DaggerFragment(), AaruyBaseNavigator {
    @Inject
    @AaruyPluginQualifier
    lateinit var viewModelFactory: ViewModelProvider.Factory

    protected var baseActivity: AaruyBaseActivity<*>? = null

    protected lateinit var binding: B

    private val compositeDisposable = CompositeDisposable()

    @LayoutRes
    abstract fun getLayoutId(): Int

    @CallSuper
    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is AaruyBaseActivity<*>) {
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
