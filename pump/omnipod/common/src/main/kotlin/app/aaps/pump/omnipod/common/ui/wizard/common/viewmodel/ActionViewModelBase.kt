package app.aaps.pump.omnipod.common.ui.wizard.common.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.rx.AapsSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import javax.inject.Provider

abstract class ActionViewModelBase(
    protected val pumpEnactResultProvider: Provider<PumpEnactResult>,
    protected val logger: AAPSLogger,
    private val aapsSchedulers: AapsSchedulers
) : ViewModelBase() {

    protected val disposable = CompositeDisposable()

    private val _isActionExecutingLiveData = MutableLiveData(false)
    val isActionExecutingLiveData: LiveData<Boolean> = _isActionExecutingLiveData

    private val _actionResultLiveData = MutableLiveData<PumpEnactResult?>(null)
    val actionResultLiveData: LiveData<PumpEnactResult?> = _actionResultLiveData

    fun executeAction() {
        _isActionExecutingLiveData.postValue(true)
        disposable += doExecuteAction()
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.main)
            .subscribeBy(
                onSuccess = { result ->
                    _isActionExecutingLiveData.postValue(false)
                    _actionResultLiveData.postValue(result)
                },
                onError = { throwable ->
                    logger.error(LTag.PUMP, "Caught exception in while executing action in ActionViewModelBase", throwable)
                    _isActionExecutingLiveData.postValue(false)
                    _actionResultLiveData.postValue(
                        pumpEnactResultProvider.get().success(false).comment(
                            throwable.message ?: "Caught exception in while executing action in ActionViewModelBase"
                        )
                    )
                })
    }

    override fun onCleared() {
        super.onCleared()
        disposable.clear()
    }

    protected abstract fun doExecuteAction(): Single<PumpEnactResult>
}
