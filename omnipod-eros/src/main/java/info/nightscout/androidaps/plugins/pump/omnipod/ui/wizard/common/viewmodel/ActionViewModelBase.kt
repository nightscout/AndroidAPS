package info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard.common.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import info.nightscout.androidaps.data.PumpEnactResult
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.SingleSubject

abstract class ActionViewModelBase : ViewModel() {

    private val _isActionExecutingLiveData = MutableLiveData<Boolean>(false)
    val isActionExecutingLiveData: LiveData<Boolean> = _isActionExecutingLiveData

    private val _actionResultLiveData = MutableLiveData<PumpEnactResult?>(null)
    val actionResultLiveData: LiveData<PumpEnactResult?> = _actionResultLiveData

    fun executeAction() {
        _isActionExecutingLiveData.postValue(true)
        SingleSubject.fromCallable<PumpEnactResult>(this::doExecuteAction)
            .subscribeOn(Schedulers.io())
            .doOnSuccess { result ->
                _isActionExecutingLiveData.postValue(false)
                _actionResultLiveData.postValue(result)
            }
            .subscribe()
    }

    protected abstract fun doExecuteAction(): PumpEnactResult
}
