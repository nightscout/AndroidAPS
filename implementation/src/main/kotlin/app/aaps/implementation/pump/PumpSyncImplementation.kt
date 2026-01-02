package app.aaps.implementation.pump

import app.aaps.core.data.model.BS
import app.aaps.core.data.model.CA
import app.aaps.core.data.model.EB
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.IDs
import app.aaps.core.data.model.TB
import app.aaps.core.data.model.TDD
import app.aaps.core.data.model.TE
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.VirtualPump
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventNewNotification
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.LongNonKey
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.extensions.asAnnouncement
import app.aaps.core.ui.R
import app.aaps.implementation.extensions.toUeSource
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject

class PumpSyncImplementation @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val dateUtil: DateUtil,
    private val preferences: Preferences,
    private val rxBus: RxBus,
    private val rh: ResourceHelper,
    private val profileFunction: ProfileFunction,
    private val persistenceLayer: PersistenceLayer,
    private val activePlugin: ActivePlugin
) : PumpSync {

    private val disposable = CompositeDisposable()

    override fun connectNewPump(endRunning: Boolean) {
        if (endRunning) {
            expectedPumpState().temporaryBasal?.let {
                syncStopTemporaryBasalWithPumpId(dateUtil.now(), dateUtil.now(), it.pumpType, it.pumpSerial)
            }
            expectedPumpState().extendedBolus?.let {
                syncStopExtendedBolusWithPumpId(dateUtil.now(), dateUtil.now(), it.pumpType, it.pumpSerial)
            }
        }
        preferences.remove(StringNonKey.ActivePumpType)
        preferences.remove(StringNonKey.ActivePumpSerialNumber)
        preferences.remove(LongNonKey.ActivePumpChangeTimestamp)
    }

    override fun verifyPumpIdentification(type: PumpType, serialNumber: String): Boolean {
        val storedType = preferences.get(StringNonKey.ActivePumpType)
        val storedSerial = preferences.get(StringNonKey.ActivePumpSerialNumber)
        if (activePlugin.activePump is VirtualPump) return true
        if (type.description == storedType && serialNumber == storedSerial) return true
        aapsLogger.debug(LTag.PUMP, "verifyPumpIdentification failed for $type $serialNumber")
        return false
    }

    /**
     * Check if data is coming from currently active pump to prevent overlapping pump histories
     *
     * @param timestamp     timestamp of data coming from pump
     * @param type          timestamp of of pump
     * @param serialNumber  serial number  of of pump
     * @return true if data is allowed
     */
    private fun confirmActivePump(timestamp: Long, type: PumpType, serialNumber: String, showNotification: Boolean = true): Boolean {
        val storedType = preferences.get(StringNonKey.ActivePumpType)
        val storedSerial = preferences.get(StringNonKey.ActivePumpSerialNumber)
        val storedTimestamp = preferences.get(LongNonKey.ActivePumpChangeTimestamp)

        // If no value stored assume we start using new pump from now
        if (storedType.isEmpty() || storedSerial.isEmpty()) {
            aapsLogger.debug(LTag.PUMP, "Registering new pump ${type.description} $serialNumber")
            preferences.put(StringNonKey.ActivePumpType, type.description)
            preferences.put(StringNonKey.ActivePumpSerialNumber, serialNumber)
            preferences.put(LongNonKey.ActivePumpChangeTimestamp, dateUtil.now()) // allow only data newer than register time (ie. ignore older history)
            return timestamp > dateUtil.now() - T.mins(1).msecs() // allow first record to be 1 min old
        }

        if (activePlugin.activePump is VirtualPump || (type.description == storedType && serialNumber == storedSerial && timestamp >= storedTimestamp)) {
            // data match
            return true
        }

        if (showNotification && (type.description != storedType || serialNumber != storedSerial) && timestamp >= storedTimestamp)
            rxBus.send(EventNewNotification(Notification(Notification.WRONG_PUMP_DATA, rh.gs(R.string.wrong_pump_data), Notification.URGENT)))
        aapsLogger.error(
            LTag.PUMP,
            "Ignoring pump history record  Allowed: ${dateUtil.dateAndTimeAndSecondsString(storedTimestamp)} $storedType $storedSerial Received: $timestamp ${
                dateUtil.dateAndTimeAndSecondsString(timestamp)
            } ${type.description} $serialNumber"
        )
        return false
    }

    override fun expectedPumpState(): PumpSync.PumpState {
        val bolus = persistenceLayer.getNewestBolus()
        val temporaryBasal = persistenceLayer.getTemporaryBasalActiveAt(dateUtil.now())
        val extendedBolus = persistenceLayer.getExtendedBolusActiveAt(dateUtil.now())

        return PumpSync.PumpState(
            temporaryBasal =
            if (temporaryBasal != null)
                PumpSync.PumpState.TemporaryBasal(
                    id = temporaryBasal.id,
                    timestamp = temporaryBasal.timestamp,
                    duration = temporaryBasal.duration,
                    rate = temporaryBasal.rate,
                    isAbsolute = temporaryBasal.isAbsolute,
                    type = PumpSync.TemporaryBasalType.fromDbType(temporaryBasal.type),
                    pumpId = temporaryBasal.ids.pumpId,
                    pumpType = temporaryBasal.ids.pumpType ?: PumpType.USER,
                    pumpSerial = temporaryBasal.ids.pumpSerial ?: "",
                )
            else null,
            extendedBolus = extendedBolus?.let {
                PumpSync.PumpState.ExtendedBolus(
                    timestamp = extendedBolus.timestamp,
                    duration = extendedBolus.duration,
                    amount = extendedBolus.amount,
                    rate = extendedBolus.rate,
                    pumpType = extendedBolus.ids.pumpType ?: PumpType.USER,
                    pumpSerial = extendedBolus.ids.pumpSerial ?: ""
                )
            },
            bolus = bolus?.let {
                PumpSync.PumpState.Bolus(
                    timestamp = bolus.timestamp,
                    amount = bolus.amount
                )
            },
            profile = profileFunction.getProfile(),
            serialNumber = preferences.get(StringNonKey.ActivePumpSerialNumber)
        )
    }

    override fun addBolusWithTempId(timestamp: Long, amount: Double, temporaryId: Long, type: BS.Type, pumpType: PumpType, pumpSerial: String): Boolean {
        if (!confirmActivePump(timestamp, pumpType, pumpSerial)) return false
        val bolus = BS(
            timestamp = timestamp,
            amount = amount,
            type = type,
            ids = IDs(
                temporaryId = temporaryId,
                pumpType = pumpType,
                pumpSerial = pumpSerial
            )
        )
        return persistenceLayer.insertBolusWithTempId(bolus)
            .map { result -> result.inserted.isNotEmpty() }
            .blockingGet()
    }

    override fun syncBolusWithTempId(timestamp: Long, amount: Double, temporaryId: Long, type: BS.Type?, pumpId: Long?, pumpType: PumpType, pumpSerial: String): Boolean {
        if (!confirmActivePump(timestamp, pumpType, pumpSerial)) return false
        val bolus = BS(
            timestamp = timestamp,
            amount = amount,
            type = BS.Type.NORMAL, // not used for update
            ids = IDs(
                temporaryId = temporaryId,
                pumpId = pumpId,
                pumpType = pumpType,
                pumpSerial = pumpSerial
            )
        )
        return persistenceLayer.syncPumpBolusWithTempId(bolus, type)
            .map { result -> result.updated.isNotEmpty() }
            .blockingGet()
    }

    override fun syncBolusWithPumpId(timestamp: Long, amount: Double, type: BS.Type?, pumpId: Long, pumpType: PumpType, pumpSerial: String): Boolean {
        if (!confirmActivePump(timestamp, pumpType, pumpSerial)) return false
        val bolus = BS(
            timestamp = timestamp,
            amount = amount,
            type = type ?: BS.Type.NORMAL,
            ids = IDs(
                pumpId = pumpId,
                pumpType = pumpType,
                pumpSerial = pumpSerial
            )
        )
        return persistenceLayer.syncPumpBolus(bolus, type)
            .map { result -> result.inserted.isNotEmpty() }
            .blockingGet()
    }

    override fun syncCarbsWithTimestamp(timestamp: Long, amount: Double, pumpId: Long?, pumpType: PumpType, pumpSerial: String): Boolean {
        if (!confirmActivePump(timestamp, pumpType, pumpSerial)) return false
        val carbs = CA(
            timestamp = timestamp,
            amount = amount,
            duration = 0,
            ids = IDs(
                pumpId = pumpId,
                pumpType = pumpType,
                pumpSerial = pumpSerial
            )
        )
        return persistenceLayer.insertPumpCarbsIfNewByTimestamp(carbs)
            .map { result -> result.inserted.isNotEmpty() }
            .blockingGet()
    }

    override fun insertTherapyEventIfNewWithTimestamp(timestamp: Long, type: TE.Type, note: String?, pumpId: Long?, pumpType: PumpType, pumpSerial: String): Boolean {
        if (!confirmActivePump(timestamp, pumpType, pumpSerial)) return false
        val therapyEvent = TE(
            timestamp = timestamp,
            type = type,
            duration = 0,
            note = note,
            enteredBy = "AndroidAPS",
            glucose = null,
            glucoseType = null,
            glucoseUnit = GlucoseUnit.MGDL,
            ids = IDs(
                pumpId = pumpId,
                pumpType = pumpType,
                pumpSerial = pumpSerial
            )
        )
        return persistenceLayer.insertPumpTherapyEventIfNewByTimestamp(
            therapyEvent = therapyEvent,
            action = Action.CAREPORTAL,
            source = pumpType.source.toUeSource(),
            note = note,
            timestamp = timestamp,
            listValues = listOf(ValueWithUnit.Timestamp(timestamp), ValueWithUnit.TEType(type))
        )
            .map { result -> result.inserted.isNotEmpty() }
            .blockingGet()
    }

    override fun insertFingerBgIfNewWithTimestamp(timestamp: Long, glucose: Double, glucoseUnit: GlucoseUnit, note: String?, pumpId: Long?, pumpType: PumpType, pumpSerial: String): Boolean {
        if (!confirmActivePump(timestamp, pumpType, pumpSerial)) return false
        val therapyEvent = TE(
            timestamp = timestamp,
            type = TE.Type.FINGER_STICK_BG_VALUE,
            duration = 0,
            note = note,
            enteredBy = "AndroidAPS",
            glucose = glucose,
            glucoseType = TE.MeterType.FINGER,
            glucoseUnit = glucoseUnit,
            ids = IDs(
                pumpId = pumpId,
                pumpType = pumpType,
                pumpSerial = pumpSerial
            )
        )
        return persistenceLayer.insertPumpTherapyEventIfNewByTimestamp(
            therapyEvent = therapyEvent,
            timestamp = timestamp,
            action = Action.CAREPORTAL,
            source = Sources.Pump,
            note = note,
            listValues = listOf(ValueWithUnit.Timestamp(timestamp), ValueWithUnit.TEType(TE.Type.FINGER_STICK_BG_VALUE))
        )
            .map { result -> result.inserted.isNotEmpty() }
            .blockingGet()
    }

    override fun insertAnnouncement(error: String, pumpId: Long?, pumpType: PumpType, pumpSerial: String) {
        if (!confirmActivePump(dateUtil.now(), pumpType, pumpSerial)) return
        disposable += persistenceLayer.insertPumpTherapyEventIfNewByTimestamp(
            therapyEvent = TE.asAnnouncement(error, pumpId, pumpType, pumpSerial),
            timestamp = dateUtil.now(),
            action = Action.TREATMENT,
            source = Sources.Pump,
            note = error,
            listValues = listOf()
        ).subscribe()
    }

    /*
     *   TEMPORARY BASALS
     */

    override fun syncTemporaryBasalWithPumpId(
        timestamp: Long,
        rate: Double,
        duration: Long,
        isAbsolute: Boolean,
        type: PumpSync.TemporaryBasalType?,
        pumpId: Long,
        pumpType: PumpType,
        pumpSerial: String
    ): Boolean {
        if (!confirmActivePump(timestamp, pumpType, pumpSerial)) return false
        val temporaryBasal = TB(
            timestamp = timestamp,
            rate = rate,
            duration = duration,
            type = type?.toDbType() ?: TB.Type.NORMAL,
            isAbsolute = isAbsolute,
            ids = IDs(
                pumpId = pumpId,
                pumpType = pumpType,
                pumpSerial = pumpSerial
            )
        )
        return persistenceLayer.syncPumpTemporaryBasal(temporaryBasal, type?.toDbType())
            .map { result -> result.inserted.isNotEmpty() }
            .blockingGet()
    }

    override fun syncStopTemporaryBasalWithPumpId(timestamp: Long, endPumpId: Long, pumpType: PumpType, pumpSerial: String, ignorePumpIds: Boolean): Boolean {
        if (!ignorePumpIds && !confirmActivePump(timestamp, pumpType, pumpSerial)) return false
        return persistenceLayer.syncPumpCancelTemporaryBasalIfAny(timestamp, endPumpId, pumpType, pumpSerial)
            .map { result -> result.updated.isNotEmpty() }
            .blockingGet()
    }

    override fun addTemporaryBasalWithTempId(
        timestamp: Long,
        rate: Double,
        duration: Long,
        isAbsolute: Boolean,
        tempId: Long,
        type: PumpSync.TemporaryBasalType,
        pumpType: PumpType,
        pumpSerial: String
    ): Boolean {
        if (!confirmActivePump(timestamp, pumpType, pumpSerial)) return false
        val temporaryBasal = TB(
            timestamp = timestamp,
            rate = rate,
            duration = duration,
            type = type.toDbType(),
            isAbsolute = isAbsolute,
            ids = IDs(
                temporaryId = tempId,
                pumpType = pumpType,
                pumpSerial = pumpSerial
            )
        )
        return persistenceLayer.insertTemporaryBasalWithTempId(temporaryBasal)
            .map { result -> result.inserted.isNotEmpty() }
            .blockingGet()
    }

    override fun syncTemporaryBasalWithTempId(
        timestamp: Long,
        rate: Double,
        duration: Long,
        isAbsolute: Boolean,
        temporaryId: Long,
        type: PumpSync.TemporaryBasalType?,
        pumpId: Long?,
        pumpType: PumpType,
        pumpSerial: String
    ): Boolean {
        if (!confirmActivePump(timestamp, pumpType, pumpSerial)) return false
        val temporaryBasal = TB(
            timestamp = timestamp,
            rate = rate,
            duration = duration,
            type = TB.Type.NORMAL, // not used for update
            isAbsolute = isAbsolute,
            ids = IDs(
                temporaryId = temporaryId,
                pumpId = pumpId,
                pumpType = pumpType,
                pumpSerial = pumpSerial
            )
        )
        return persistenceLayer.syncPumpTemporaryBasalWithTempId(temporaryBasal, type?.toDbType())
            .map { result -> result.updated.isNotEmpty() }
            .blockingGet()
    }

    override fun invalidateTemporaryBasal(id: Long, sources: Sources, timestamp: Long): Boolean =
        persistenceLayer.invalidateTemporaryBasal(
            id = id,
            action = Action.TEMP_BASAL_REMOVED,
            source = sources,
            note = null,
            listValues = listOf(ValueWithUnit.Timestamp(timestamp))
        ).map { result -> result.invalidated.isNotEmpty() }
            .blockingGet()

    override fun invalidateTemporaryBasalWithPumpId(pumpId: Long, pumpType: PumpType, pumpSerial: String): Boolean =
        persistenceLayer.syncPumpInvalidateTemporaryBasalWithPumpId(pumpId, pumpType, pumpSerial)
            .map { result -> result.invalidated.isNotEmpty() }
            .blockingGet()

    override fun invalidateTemporaryBasalWithTempId(temporaryId: Long): Boolean =
        persistenceLayer.syncPumpInvalidateTemporaryBasalWithTempId(temporaryId)
            .map { result -> result.invalidated.isNotEmpty() }
            .blockingGet()

    override fun syncExtendedBolusWithPumpId(timestamp: Long, amount: Double, duration: Long, isEmulatingTB: Boolean, pumpId: Long, pumpType: PumpType, pumpSerial: String): Boolean {
        if (!confirmActivePump(timestamp, pumpType, pumpSerial)) return false
        val extendedBolus = EB(
            timestamp = timestamp,
            amount = amount,
            duration = duration,
            isEmulatingTempBasal = isEmulatingTB,
            ids = IDs(
                pumpId = pumpId,
                pumpType = pumpType,
                pumpSerial = pumpSerial
            )
        )
        return persistenceLayer.syncPumpExtendedBolus(extendedBolus)
            .map { result -> result.inserted.isNotEmpty() }
            .blockingGet()
    }

    override fun syncStopExtendedBolusWithPumpId(timestamp: Long, endPumpId: Long, pumpType: PumpType, pumpSerial: String): Boolean {
        if (!confirmActivePump(timestamp, pumpType, pumpSerial)) return false
        return persistenceLayer.syncPumpStopExtendedBolusWithPumpId(timestamp, endPumpId, pumpType, pumpSerial)
            .map { result -> result.updated.isNotEmpty() }
            .blockingGet()
    }

    override fun createOrUpdateTotalDailyDose(timestamp: Long, bolusAmount: Double, basalAmount: Double, totalAmount: Double, pumpId: Long?, pumpType: PumpType, pumpSerial: String): Boolean {
        // there are probably old data in pump -> do not show notification, just ignore
        if (!confirmActivePump(timestamp, pumpType, pumpSerial, showNotification = false)) return false
        val tdd = TDD(
            timestamp = timestamp,
            bolusAmount = bolusAmount,
            basalAmount = basalAmount,
            totalAmount = totalAmount,
            ids = IDs(
                pumpId = pumpId,
                pumpType = pumpType,
                pumpSerial = pumpSerial
            )
        )
        return persistenceLayer.insertOrUpdateTotalDailyDose(tdd)
            .map { result -> result.inserted.isNotEmpty() || result.updated.isNotEmpty() }
            .blockingGet()
    }
}