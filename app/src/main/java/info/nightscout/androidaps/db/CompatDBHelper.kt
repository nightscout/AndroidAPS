package info.nightscout.androidaps.db

import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.entities.GlucoseValue
import info.nightscout.androidaps.database.entities.TemporaryTarget
import info.nightscout.androidaps.database.interfaces.TraceableDBEntry
import info.nightscout.androidaps.events.EventNewBG
import info.nightscout.androidaps.events.EventTempTargetChange
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.events.EventNewHistoryData
import io.reactivex.disposables.Disposable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CompatDBHelper @Inject constructor(
    val aapsLogger: AAPSLogger,
    val repository: AppRepository,
    val rxBus: RxBusWrapper
) {

    fun dbChangeDisposable(): Disposable = repository
        .changeObservable()
        .doOnSubscribe {
            rxBus.send(EventNewBG(null))
        }
        .subscribe {
            it.filterIsInstance<GlucoseValue>().firstOrNull()?.let {
                aapsLogger.debug(LTag.DATABASE, "Firing EventNewHistoryData")
                rxBus.send(EventNewHistoryData(it.timestamp))
            }
            it.filterIsInstance<GlucoseValue>().lastOrNull()?.let {
                aapsLogger.debug(LTag.DATABASE, "Firing EventNewBg")
                rxBus.send(EventNewBG(it))
            }
            it.filterIsInstance<TemporaryTarget>().firstOrNull()?.let {
                aapsLogger.debug(LTag.DATABASE, "Firing EventTempTargetChange")
                rxBus.send(EventTempTargetChange())
            }
        }
}