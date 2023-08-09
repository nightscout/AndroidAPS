package info.nightscout.implementation.db

import android.content.Context
import info.nightscout.database.entities.Bolus
import info.nightscout.database.entities.Carbs
import info.nightscout.database.entities.DeviceStatus
import info.nightscout.database.entities.EffectiveProfileSwitch
import info.nightscout.database.entities.ExtendedBolus
import info.nightscout.database.entities.Food
import info.nightscout.database.entities.GlucoseValue
import info.nightscout.database.entities.OfflineEvent
import info.nightscout.database.entities.ProfileSwitch
import info.nightscout.database.entities.TemporaryBasal
import info.nightscout.database.entities.TemporaryTarget
import info.nightscout.database.entities.TherapyEvent
import info.nightscout.database.impl.AppRepository
import info.nightscout.interfaces.ui.UiInteraction
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventDeviceStatusChange
import info.nightscout.rx.events.EventEffectiveProfileSwitchChanged
import info.nightscout.rx.events.EventExtendedBolusChange
import info.nightscout.rx.events.EventFoodDatabaseChanged
import info.nightscout.rx.events.EventNewBG
import info.nightscout.rx.events.EventNewHistoryData
import info.nightscout.rx.events.EventOfflineChange
import info.nightscout.rx.events.EventProfileSwitchChanged
import info.nightscout.rx.events.EventTempBasalChange
import info.nightscout.rx.events.EventTempTargetChange
import info.nightscout.rx.events.EventTherapyEventChange
import info.nightscout.rx.events.EventTreatmentChange
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import io.reactivex.rxjava3.disposables.Disposable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CompatDBHelper @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val repository: AppRepository,
    private val rxBus: RxBus,
    private val context: Context,
    private val uiInteraction: UiInteraction
) {

    fun dbChangeDisposable(): Disposable = repository
        .changeObservable()
        .doOnSubscribe {
            rxBus.send(EventNewBG(null))
            uiInteraction.updateWidget(context, "OnStart")
        }
        .subscribe {
            /**
             * GlucoseValues can come in batch
             * oldest one should be used for invalidation, newest one for for triggering Loop.
             * Thus we need to collect both
             *
             */
            var newestGlucoseValue: GlucoseValue? = null
            it.filterIsInstance<GlucoseValue>().maxByOrNull { gv -> gv.timestamp }?.let { gv ->
                aapsLogger.debug(LTag.DATABASE, "Firing EventNewBg $gv")
                rxBus.send(EventNewBG(gv.timestamp))
                newestGlucoseValue = gv
            }
            it.filterIsInstance<GlucoseValue>().minOfOrNull { gv -> gv.timestamp }?.let { timestamp ->
                aapsLogger.debug(LTag.DATABASE, "Firing EventNewHistoryData $timestamp $newestGlucoseValue")
                rxBus.send(EventNewHistoryData(timestamp, true, newestGlucoseValue?.timestamp))
            }
            it.filterIsInstance<Carbs>().minOfOrNull { t -> t.timestamp }?.let { timestamp ->
                aapsLogger.debug(LTag.DATABASE, "Firing EventTreatmentChange $timestamp")
                rxBus.send(EventTreatmentChange())
                rxBus.send(EventNewHistoryData(timestamp, false))
            }
            it.filterIsInstance<Bolus>().minOfOrNull { t -> t.timestamp }?.let { timestamp ->
                aapsLogger.debug(LTag.DATABASE, "Firing EventTreatmentChange $timestamp")
                rxBus.send(EventTreatmentChange())
                rxBus.send(EventNewHistoryData(timestamp, false))
            }
            it.filterIsInstance<TemporaryBasal>().minOfOrNull { t -> t.timestamp }?.let { timestamp ->
                aapsLogger.debug(LTag.DATABASE, "Firing EventTempBasalChange $timestamp")
                rxBus.send(EventTempBasalChange())
                rxBus.send(EventNewHistoryData(timestamp, false))
            }
            it.filterIsInstance<ExtendedBolus>().minOfOrNull { t -> t.timestamp }?.let { timestamp ->
                aapsLogger.debug(LTag.DATABASE, "Firing EventExtendedBolusChange $timestamp")
                rxBus.send(EventExtendedBolusChange())
                rxBus.send(EventNewHistoryData(timestamp, false))
            }
            it.filterIsInstance<EffectiveProfileSwitch>().firstOrNull()?.let { eps ->
                aapsLogger.debug(LTag.DATABASE, "Firing EventEffectiveProfileSwitchChanged $eps")
                rxBus.send(EventEffectiveProfileSwitchChanged(eps.timestamp))
                rxBus.send(EventNewHistoryData(eps.timestamp, false))
            }
            it.filterIsInstance<TemporaryTarget>().firstOrNull()?.let { tt ->
                aapsLogger.debug(LTag.DATABASE, "Firing EventTempTargetChange $tt")
                rxBus.send(EventTempTargetChange())
            }
            it.filterIsInstance<TherapyEvent>().firstOrNull()?.let { te ->
                aapsLogger.debug(LTag.DATABASE, "Firing EventTherapyEventChange $te")
                rxBus.send(EventTherapyEventChange())
            }
            it.filterIsInstance<Food>().firstOrNull()?.let { food ->
                aapsLogger.debug(LTag.DATABASE, "Firing EventFoodDatabaseChanged $food")
                rxBus.send(EventFoodDatabaseChanged())
            }
            it.filterIsInstance<ProfileSwitch>().firstOrNull()?.let { ps ->
                aapsLogger.debug(LTag.DATABASE, "Firing EventProfileSwitchChanged $ps")
                rxBus.send(EventProfileSwitchChanged())
            }
            it.filterIsInstance<OfflineEvent>().firstOrNull()?.let { oe ->
                aapsLogger.debug(LTag.DATABASE, "Firing EventOfflineChange $oe")
                rxBus.send(EventOfflineChange())
            }
            it.filterIsInstance<DeviceStatus>().firstOrNull()?.let { ds ->
                aapsLogger.debug(LTag.DATABASE, "Firing EventDeviceStatusChange $ds")
                rxBus.send(EventDeviceStatusChange())
            }
        }
}