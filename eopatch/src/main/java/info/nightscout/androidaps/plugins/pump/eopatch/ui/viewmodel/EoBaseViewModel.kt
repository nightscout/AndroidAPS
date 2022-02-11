package info.nightscout.androidaps.plugins.pump.eopatch.ui.viewmodel

import android.view.MotionEvent
import android.view.View
import androidx.annotation.Keep
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

    fun blockTouchEvent(view: View, motionEvent: MotionEvent): Boolean {
        return true
    }

    fun back() = navigator?.back()

    fun finish() = navigator?.finish()

    fun Disposable.addTo() = addTo(compositeDisposable)

}