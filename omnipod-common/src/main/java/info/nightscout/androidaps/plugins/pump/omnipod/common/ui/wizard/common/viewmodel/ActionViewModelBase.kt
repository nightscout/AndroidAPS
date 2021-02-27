package info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.common.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import io.reactivex.Single
import javax.inject.Inject

abstract class ActionViewModelBase : ViewModelBase() {

    @Inject lateinit var injector: HasAndroidInjector
    @Inject lateinit var logger: AAPSLogger

    private val _isActionExecutingLiveData = MutableLiveData(false)
    val isActionExecutingLiveData: LiveData<Boolean> = _isActionExecutingLiveData

    private val _actionResultLiveData = MutableLiveData<PumpEnactResult?>(null)
    val actionResultLiveData: LiveData<PumpEnactResult?> = _actionResultLiveData

    fun executeAction() {
        _isActionExecutingLiveData.postValue(true)
        val disposable = doExecuteAction().subscribe({ result ->
            _isActionExecutingLiveData.postValue(false)
            _actionResultLiveData.postValue(result)
        }, { throwable ->
            logger.error(LTag.PUMP, "Caught exception in while executing action in ActionViewModelBase", throwable)
            _isActionExecutingLiveData.postValue(false)
            _actionResultLiveData.postValue(PumpEnactResult(injector).success(false).comment(throwable.message))
        })
    }

    protected abstract fun doExecuteAction(): Single<PumpEnactResult>
}
