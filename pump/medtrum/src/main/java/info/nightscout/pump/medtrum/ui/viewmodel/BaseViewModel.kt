package info.nightscout.pump.medtrum.ui.viewmodel

import androidx.lifecycle.ViewModel
import info.nightscout.pump.medtrum.code.PatchStep
import info.nightscout.pump.medtrum.comm.enums.MedtrumPumpState
import info.nightscout.pump.medtrum.ui.MedtrumBaseNavigator
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import java.lang.ref.WeakReference

abstract class BaseViewModel<N : MedtrumBaseNavigator> : ViewModel() {

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

    fun Disposable.addTo() = apply { compositeDisposable.add(this) }

    fun convertToPatchStep(pumpState: MedtrumPumpState) = when (pumpState) {
        MedtrumPumpState.NONE, MedtrumPumpState.IDLE         -> PatchStep.PREPARE_PATCH
        MedtrumPumpState.FILLED                              -> PatchStep.PREPARE_PATCH
        MedtrumPumpState.PRIMING                             -> PatchStep.PRIME
        MedtrumPumpState.PRIMED, MedtrumPumpState.EJECTED    -> PatchStep.ATTACH_PATCH
        MedtrumPumpState.ACTIVE, MedtrumPumpState.ACTIVE_ALT -> PatchStep.COMPLETE
        MedtrumPumpState.STOPPED                             -> PatchStep.DEACTIVATION_COMPLETE
        else                                                 -> PatchStep.CANCEL
    }
}