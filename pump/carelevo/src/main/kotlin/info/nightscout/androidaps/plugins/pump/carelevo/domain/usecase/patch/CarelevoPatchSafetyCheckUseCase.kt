package info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.patch

import info.nightscout.androidaps.plugins.pump.carelevo.domain.CarelevoPatchObserver
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.RequestResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.SafetyCheckResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.SafetyCheckResultModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoPatchInfoRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoPatchRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.type.SafetyProgress
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.joda.time.DateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class CarelevoPatchSafetyCheckUseCase @Inject constructor(
    private val patchObserver: CarelevoPatchObserver,
    private val patchRepository: CarelevoPatchRepository,
    private val patchInfoRepository: CarelevoPatchInfoRepository
) {
    fun execute(): Observable<SafetyProgress> {
        return patchRepository.requestSafetyCheck()
            .subscribeOn(Schedulers.io())
            .flatMapObservable { req ->
                if (req !is RequestResult.Pending) {
                    return@flatMapObservable Observable.error(
                        IllegalStateException("request safety check is not pending")
                    )
                }

                val requestReplySingle = patchObserver.patchEvent
                    .ofType(SafetyCheckResultModel::class.java)
                    .filter { it.result == SafetyCheckResult.REP_REQUEST || it.result == SafetyCheckResult.REP_REQUEST1 }
                    .firstOrError()
                    .timeout(100, TimeUnit.SECONDS)

                requestReplySingle
                    .toObservable()
                    .flatMap { requestReply ->
                        val timeoutSec = (requestReply.durationSeconds + 30).toLong()

                        val progress: Observable<SafetyProgress> = Observable.just(SafetyProgress.Progress(timeoutSec))

                        val success: Observable<SafetyProgress> =
                            patchObserver.patchEvent
                                .ofType(SafetyCheckResultModel::class.java)
                                .filter { it.result == SafetyCheckResult.SUCCESS }
                                .firstOrError()
                                .timeout(timeoutSec, TimeUnit.SECONDS)
                                .toObservable()
                                .map {
                                    val patchInfo = patchInfoRepository.getPatchInfoBySync()
                                        ?: return@map SafetyProgress.Error(NullPointerException("patch info must be not null"))

                                    val ok = patchInfoRepository.updatePatchInfo(
                                        patchInfo.copy(checkSafety = true, updatedAt = DateTime.now())
                                    )
                                    if (!ok) return@map SafetyProgress.Error(IllegalStateException("update patch info is failed"))

                                    SafetyProgress.Success(it)
                                }


                        progress.concatWith(success)
                    }
            }
            .onErrorReturn { SafetyProgress.Error(it) }
    }
}
