package info.nightscout.androidaps.plugins.pump.carelevo.domain

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.AapsSchedulers
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.ResponseResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.PatchResultModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.createBasalResultModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.createBolusResultModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.createPatchResultModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoBasalRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoBolusRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoPatchRepository
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.PublishSubject
import javax.inject.Inject

class CarelevoPatchObserver @Inject constructor(
    private val patchRepository: CarelevoPatchRepository,
    private val basalRepository: CarelevoBasalRepository,
    private val bolusRepository: CarelevoBolusRepository,
    private val aapsSchedulers: AapsSchedulers,
    private val aapsLogger: AAPSLogger
) {

    private val bleDisposable = CompositeDisposable()

    private val _patchEvent: PublishSubject<PatchResultModel> = PublishSubject.create()
    internal val patchEvent get() = _patchEvent

    private val _basalEvent: PublishSubject<PatchResultModel> = PublishSubject.create()
    internal val basalEvent get() = _basalEvent

    private val _bolusEvent: PublishSubject<PatchResultModel> = PublishSubject.create()
    internal val bolusEvent get() = _bolusEvent

    private val _patchResponseEvent: PublishSubject<PatchResultModel> = PublishSubject.create()
    internal val patchResponseEvent get() = _patchResponseEvent

    private var _isObserverWorking = false
    val isObserverWorking get() = _isObserverWorking

    private val observeSchedulers = Schedulers.single();

    init {
        initPatchObserver()
    }

    private fun initPatchObserver() {
        if (!isObserverWorking) {
            observePatch()
            observeBasal()
            observeBolus()
            _isObserverWorking = true
        }
    }

    private fun observePatch() {
        bleDisposable += patchRepository.getResponseResult()
            .observeOn(observeSchedulers)
            .subscribe { result ->
                if (result is ResponseResult.Success) {
                    result.data?.let {
                        createPatchResultModel(it)?.let { model ->
                            aapsLogger.debug(LTag.PUMP, "[CarelevoPatchObserver] observePatch model=$model")
                            _patchEvent.onNext(model)
                            _patchResponseEvent.onNext(model)
                        }
                    }
                }
            }
    }

    private fun observeBasal() {
        bleDisposable += basalRepository.getResponseResult()
            .observeOn(observeSchedulers)
            .subscribe { result ->
                if (result is ResponseResult.Success) {
                    result.data?.let {
                        createBasalResultModel(it)?.let { model ->
                            _basalEvent.onNext(model)
                            _patchResponseEvent.onNext(model)
                        }
                    }
                }
            }
    }

    private fun observeBolus() {
        bleDisposable += bolusRepository.getResponseResult()
            .observeOn(observeSchedulers)
            .subscribe { result ->
                if (result is ResponseResult.Success) {
                    result.data?.let {
                        createBolusResultModel(it)?.let { model ->
                            _bolusEvent.onNext(model)
                            _patchResponseEvent.onNext(model)
                        }
                    }
                }
            }
    }

    private fun releaseObserver() {
        if (isObserverWorking) {
            bleDisposable.clear()
            _isObserverWorking = false
        }
    }
}
