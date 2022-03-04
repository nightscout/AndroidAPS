package info.nightscout.androidaps.plugins.pump.eopatch.ui.viewmodel

import androidx.lifecycle.ViewModel
import info.nightscout.androidaps.plugins.pump.eopatch.ui.EoBaseNavigator
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.addTo
import java.lang.ref.WeakReference

abstract class EoBaseViewModel<N : EoBaseNavigator> : ViewModel() {

    private var _navigator: WeakReference<N?>? = null
    var navigator: N?
        set(value) {
            _navigator = WeakReference(value)
        }
        get() = _navigator?.get()

    private val compositeDisposable = CompositeDisposable()

    override fun onCleared() {
        compositeDisposable.clear()
        super.onCleared()
    }

    fun back() = navigator?.back()

    fun finish() = navigator?.finish()

    fun Disposable.addTo() = addTo(compositeDisposable)

}