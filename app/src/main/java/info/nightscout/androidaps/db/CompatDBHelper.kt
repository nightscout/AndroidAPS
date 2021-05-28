package info.nightscout.androidaps.db

import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.entities.*
import info.nightscout.androidaps.database.entities.ExtendedBolus
import info.nightscout.androidaps.database.entities.TemporaryBasal
import info.nightscout.androidaps.events.*
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
            /**
             * GlucoseValues can come in batch
             * oldest one should be used for invalidation, newest one for for triggering Loop.
             * Thus we need to collect both
             *
             */
            var newestGlucoseValue: GlucoseValue? = null
            it.filterIsInstance<GlucoseValue>().lastOrNull()?.let { gv ->
                aapsLogger.debug(LTag.DATABASE, "Firing EventNewBg")
                rxBus.send(EventNewBG(gv))
                newestGlucoseValue = gv
            }
            it.filterIsInstance<GlucoseValue>().map { gv -> gv.timestamp }.minOrNull()?.let { timestamp ->
                aapsLogger.debug(LTag.DATABASE, "Firing EventNewHistoryData")
                rxBus.send(EventNewHistoryData(timestamp, true, newestGlucoseValue))
            }
            it.filterIsInstance<Carbs>().map { t -> t.timestamp }.minOrNull()?.let { timestamp ->
                aapsLogger.debug(LTag.DATABASE, "Firing EventTreatmentChange")
                rxBus.send(EventTreatmentChange())
                rxBus.send(EventNewHistoryData(timestamp, false))
            }
            it.filterIsInstance<Bolus>().map { t -> t.timestamp }.minOrNull()?.let { timestamp ->
                aapsLogger.debug(LTag.DATABASE, "Firing EventTreatmentChange")
                rxBus.send(EventTreatmentChange())
                rxBus.send(EventNewHistoryData(timestamp, false))
            }
            it.filterIsInstance<TemporaryBasal>().map { t -> t.timestamp }.minOrNull()?.let { timestamp ->
                aapsLogger.debug(LTag.DATABASE, "Firing EventTempBasalChange")
                rxBus.send(EventTempBasalChange())
                rxBus.send(EventNewHistoryData(timestamp, false))
            }
            it.filterIsInstance<ExtendedBolus>().map { t -> t.timestamp }.minOrNull()?.let { timestamp ->
                aapsLogger.debug(LTag.DATABASE, "Firing EventExtendedBolusChange")
                rxBus.send(EventExtendedBolusChange())
                rxBus.send(EventNewHistoryData(timestamp, false))
            }
            it.filterIsInstance<TemporaryTarget>().firstOrNull()?.let {
                aapsLogger.debug(LTag.DATABASE, "Firing EventTempTargetChange")
                rxBus.send(EventTempTargetChange())
            }
            it.filterIsInstance<TherapyEvent>().firstOrNull()?.let {
                aapsLogger.debug(LTag.DATABASE, "Firing EventTherapyEventChange")
                rxBus.send(EventTherapyEventChange())
            }
            it.filterIsInstance<Food>().firstOrNull()?.let {
                aapsLogger.debug(LTag.DATABASE, "Firing EventFoodDatabaseChanged")
                rxBus.send(EventFoodDatabaseChanged())
            }
            it.filterIsInstance<ProfileSwitch>().firstOrNull()?.let {
                aapsLogger.debug(LTag.DATABASE, "Firing EventProfileSwitchChanged")
                rxBus.send(EventProfileSwitchChanged())
            }
            it.filterIsInstance<EffectiveProfileSwitch>().firstOrNull()?.let {
                aapsLogger.debug(LTag.DATABASE, "Firing EventProfileSwitchChanged")
                rxBus.send(EventProfileSwitchChanged())
            }
            it.filterIsInstance<OfflineEvent>().firstOrNull()?.let {
                aapsLogger.debug(LTag.DATABASE, "Firing EventOfflineChange")
                rxBus.send(EventOfflineChange())
            }
        }
}