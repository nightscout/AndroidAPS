package info.nightscout.androidaps.plugins.pump.eopatch.ui

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
import info.nightscout.androidaps.plugins.pump.eopatch.dagger.EopatchPluginQualifier
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import javax.inject.Inject
import io.reactivex.rxkotlin.addTo

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

    override fun toast(message: String) {
        baseActivity?.toast(message)
    }

    override fun toast(message: Int) {
        baseActivity?.toast(message)
    }

    override fun back() {
        baseActivity?.back()
    }

    override fun finish(finishAffinity: Boolean) {
        baseActivity?.finish(finishAffinity)
    }

    fun Disposable.addTo() = addTo(compositeDisposable)

}