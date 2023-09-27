package app.aaps.implementation.pump

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.VirtualPump
import app.aaps.core.interfaces.pump.defs.PumpType
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.T
import app.aaps.core.main.events.EventNewNotification
import app.aaps.core.main.pump.fromDbPumpType
import app.aaps.core.main.pump.toDbPumpType
import app.aaps.core.main.pump.toDbSource
import app.aaps.database.ValueWrapper
import app.aaps.database.entities.Bolus
import app.aaps.database.entities.Carbs
import app.aaps.database.entities.ExtendedBolus
import app.aaps.database.entities.TemporaryBasal
import app.aaps.database.entities.TherapyEvent
import app.aaps.database.entities.TotalDailyDose
import app.aaps.database.entities.UserEntry
import app.aaps.database.entities.ValueWithUnit
import app.aaps.database.entities.embedments.InterfaceIDs
import app.aaps.database.impl.AppRepository
import app.aaps.database.impl.transactions.InsertBolusWithTempIdTransaction
import app.aaps.database.impl.transactions.InsertIfNewByTimestampCarbsTransaction
import app.aaps.database.impl.transactions.InsertIfNewByTimestampTherapyEventTransaction
import app.aaps.database.impl.transactions.InsertTemporaryBasalWithTempIdTransaction
import app.aaps.database.impl.transactions.InsertTherapyEventAnnouncementTransaction
import app.aaps.database.impl.transactions.InvalidateTemporaryBasalTransaction
import app.aaps.database.impl.transactions.InvalidateTemporaryBasalTransactionWithPumpId
import app.aaps.database.impl.transactions.InvalidateTemporaryBasalWithTempIdTransaction
import app.aaps.database.impl.transactions.SyncBolusWithTempIdTransaction
import app.aaps.database.impl.transactions.SyncPumpBolusTransaction
import app.aaps.database.impl.transactions.SyncPumpCancelExtendedBolusIfAnyTransaction
import app.aaps.database.impl.transactions.SyncPumpCancelTemporaryBasalIfAnyTransaction
import app.aaps.database.impl.transactions.SyncPumpExtendedBolusTransaction
import app.aaps.database.impl.transactions.SyncPumpTemporaryBasalTransaction
import app.aaps.database.impl.transactions.SyncPumpTotalDailyDoseTransaction
import app.aaps.database.impl.transactions.SyncTemporaryBasalWithTempIdTransaction
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject

class PumpSyncImplementation @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val dateUtil: DateUtil,
    private val sp: SP,
    private val rxBus: RxBus,
    private val rh: ResourceHelper,
    private val profileFunction: ProfileFunction,
    private val repository: AppRepository,
    private val uel: UserEntryLogger,
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
        sp.remove(app.aaps.core.utils.R.string.key_active_pump_type)
        sp.remove(app.aaps.core.utils.R.string.key_active_pump_serial_number)
        sp.remove(app.aaps.core.utils.R.string.key_active_pump_change_timestamp)
    }

    override fun verifyPumpIdentification(type: PumpType, serialNumber: String): Boolean {
        val storedType = sp.getString(app.aaps.core.utils.R.string.key_active_pump_type, "")
        val storedSerial = sp.getString(app.aaps.core.utils.R.string.key_active_pump_serial_number, "")
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
        val storedType = sp.getString(app.aaps.core.utils.R.string.key_active_pump_type, "")
        val storedSerial = sp.getString(app.aaps.core.utils.R.string.key_active_pump_serial_number, "")
        val storedTimestamp = sp.getLong(app.aaps.core.utils.R.string.key_active_pump_change_timestamp, 0L)

        // If no value stored assume we start using new pump from now
        if (storedType.isEmpty() || storedSerial.isEmpty()) {
            aapsLogger.debug(LTag.PUMP, "Registering new pump ${type.description} $serialNumber")
            sp.putString(app.aaps.core.utils.R.string.key_active_pump_type, type.description)
            sp.putString(app.aaps.core.utils.R.string.key_active_pump_serial_number, serialNumber)
            sp.putLong(app.aaps.core.utils.R.string.key_active_pump_change_timestamp, dateUtil.now()) // allow only data newer than register time (ie. ignore older history)
            return timestamp > dateUtil.now() - T.mins(1).msecs() // allow first record to be 1 min old
        }

        if (activePlugin.activePump is VirtualPump || (type.description == storedType && serialNumber == storedSerial && timestamp >= storedTimestamp)) {
            // data match
            return true
        }

        if (showNotification && (type.description != storedType || serialNumber != storedSerial) && timestamp >= storedTimestamp)
            rxBus.send(EventNewNotification(Notification(Notification.WRONG_PUMP_DATA, rh.gs(app.aaps.core.ui.R.string.wrong_pump_data), Notification.URGENT)))
        aapsLogger.error(
            LTag.PUMP,
            "Ignoring pump history record  Allowed: ${dateUtil.dateAndTimeAndSecondsString(storedTimestamp)} $storedType $storedSerial Received: $timestamp ${
                dateUtil.dateAndTimeAndSecondsString(timestamp)
            } ${type.description} $serialNumber"
        )
        return false
    }

    override fun expectedPumpState(): PumpSync.PumpState {
        val bolus = repository.getLastBolusRecordWrapped().blockingGet()
        val temporaryBasal = repository.getTemporaryBasalActiveAt(dateUtil.now()).blockingGet()
        val extendedBolus = repository.getExtendedBolusActiveAt(dateUtil.now()).blockingGet()

        return PumpSync.PumpState(
            temporaryBasal =
            if (temporaryBasal is ValueWrapper.Existing)
                PumpSync.PumpState.TemporaryBasal(
                    id = temporaryBasal.value.id,
                    timestamp = temporaryBasal.value.timestamp,
                    duration = temporaryBasal.value.duration,
                    rate = temporaryBasal.value.rate,
                    isAbsolute = temporaryBasal.value.isAbsolute,
                    type = PumpSync.TemporaryBasalType.fromDbType(temporaryBasal.value.type),
                    pumpId = temporaryBasal.value.interfaceIDs.pumpId,
                    pumpType = temporaryBasal.value.interfaceIDs.pumpType?.let { PumpType.fromDbPumpType(it) } ?: PumpType.USER,
                    pumpSerial = temporaryBasal.value.interfaceIDs.pumpSerial ?: "",
                )
            else null,
            extendedBolus =
            if (extendedBolus is ValueWrapper.Existing)
                PumpSync.PumpState.ExtendedBolus(
                    timestamp = extendedBolus.value.timestamp,
                    duration = extendedBolus.value.duration,
                    amount = extendedBolus.value.amount,
                    rate = extendedBolus.value.rate,
                    pumpType = extendedBolus.value.interfaceIDs.pumpType?.let { PumpType.fromDbPumpType(it) } ?: PumpType.USER,
                    pumpSerial = extendedBolus.value.interfaceIDs.pumpSerial ?: ""
                )
            else null,
            bolus =
            if (bolus is ValueWrapper.Existing)
                bolus.value.let {
                    PumpSync.PumpState.Bolus(
                        timestamp = bolus.value.timestamp,
                        amount = bolus.value.amount
                    )
                }
            else null,
            profile = profileFunction.getProfile(),
            serialNumber = sp.getString(app.aaps.core.utils.R.string.key_active_pump_serial_number, "")
        )
    }

    override fun addBolusWithTempId(timestamp: Long, amount: Double, temporaryId: Long, type: DetailedBolusInfo.BolusType, pumpType: PumpType, pumpSerial: String): Boolean {
        if (!confirmActivePump(timestamp, pumpType, pumpSerial)) return false
        val bolus = Bolus(
            timestamp = timestamp,
            amount = amount,
            type = type.toDBbBolusType(),
            interfaceIDs_backing = InterfaceIDs(
                temporaryId = temporaryId,
                pumpType = pumpType.toDbPumpType(),
                pumpSerial = pumpSerial
            )
        )
        repository.runTransactionForResult(InsertBolusWithTempIdTransaction(bolus))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while saving Bolus", it) }
            .blockingGet()
            .also { result ->
                result.inserted.forEach { aapsLogger.debug(LTag.DATABASE, "Inserted Bolus $it") }
                return result.inserted.size > 0
            }
    }

    override fun syncBolusWithTempId(timestamp: Long, amount: Double, temporaryId: Long, type: DetailedBolusInfo.BolusType?, pumpId: Long?, pumpType: PumpType, pumpSerial: String): Boolean {
        if (!confirmActivePump(timestamp, pumpType, pumpSerial)) return false
        val bolus = Bolus(
            timestamp = timestamp,
            amount = amount,
            type = Bolus.Type.NORMAL, // not used for update
            interfaceIDs_backing = InterfaceIDs(
                temporaryId = temporaryId,
                pumpId = pumpId,
                pumpType = pumpType.toDbPumpType(),
                pumpSerial = pumpSerial
            )
        )
        repository.runTransactionForResult(SyncBolusWithTempIdTransaction(bolus, type?.toDBbBolusType()))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while saving Bolus", it) }
            .blockingGet()
            .also { result ->
                result.updated.forEach { aapsLogger.debug(LTag.DATABASE, "Updated Bolus $it") }
                return result.updated.size > 0
            }
    }

    override fun syncBolusWithPumpId(timestamp: Long, amount: Double, type: DetailedBolusInfo.BolusType?, pumpId: Long, pumpType: PumpType, pumpSerial: String): Boolean {
        if (!confirmActivePump(timestamp, pumpType, pumpSerial)) return false
        val bolus = Bolus(
            timestamp = timestamp,
            amount = amount,
            type = type?.toDBbBolusType() ?: Bolus.Type.NORMAL,
            interfaceIDs_backing = InterfaceIDs(
                pumpId = pumpId,
                pumpType = pumpType.toDbPumpType(),
                pumpSerial = pumpSerial
            )
        )
        repository.runTransactionForResult(SyncPumpBolusTransaction(bolus, type?.toDBbBolusType()))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while saving Bolus", it) }
            .blockingGet()
            .also { result ->
                result.inserted.forEach { aapsLogger.debug(LTag.DATABASE, "Inserted Bolus $it") }
                result.updated.forEach { aapsLogger.debug(LTag.DATABASE, "Updated Bolus $it") }
                return result.inserted.size > 0
            }
    }

    override fun syncCarbsWithTimestamp(timestamp: Long, amount: Double, pumpId: Long?, pumpType: PumpType, pumpSerial: String): Boolean {
        if (!confirmActivePump(timestamp, pumpType, pumpSerial)) return false
        val carbs = Carbs(
            timestamp = timestamp,
            amount = amount,
            duration = 0,
            interfaceIDs_backing = InterfaceIDs(
                pumpId = pumpId,
                pumpType = pumpType.toDbPumpType(),
                pumpSerial = pumpSerial
            )
        )
        repository.runTransactionForResult(InsertIfNewByTimestampCarbsTransaction(carbs))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while saving Carbs", it) }
            .blockingGet()
            .also { result ->
                result.inserted.forEach { aapsLogger.debug(LTag.DATABASE, "Inserted Carbs $it") }
                return result.inserted.size > 0
            }
    }

    override fun insertTherapyEventIfNewWithTimestamp(timestamp: Long, type: DetailedBolusInfo.EventType, note: String?, pumpId: Long?, pumpType: PumpType, pumpSerial: String): Boolean {
        if (!confirmActivePump(timestamp, pumpType, pumpSerial)) return false
        val therapyEvent = TherapyEvent(
            timestamp = timestamp,
            type = type.toDBbEventType(),
            duration = 0,
            note = note,
            enteredBy = "AndroidAPS",
            glucose = null,
            glucoseType = null,
            glucoseUnit = TherapyEvent.GlucoseUnit.MGDL,
            interfaceIDs_backing = InterfaceIDs(
                pumpId = pumpId,
                pumpType = pumpType.toDbPumpType(),
                pumpSerial = pumpSerial
            )
        )
        uel.log(
            action = UserEntry.Action.CAREPORTAL,
            source = pumpType.source.toDbSource(),
            note = note,
            timestamp = timestamp,
            ValueWithUnit.Timestamp(timestamp), ValueWithUnit.TherapyEventType(type.toDBbEventType())
        )
        repository.runTransactionForResult(InsertIfNewByTimestampTherapyEventTransaction(therapyEvent))
            .doOnError {
                aapsLogger.error(LTag.DATABASE, "Error while saving TherapyEvent", it)
            }
            .blockingGet()
            .also { result ->
                result.inserted.forEach { aapsLogger.debug(LTag.DATABASE, "Inserted TherapyEvent $it") }
                return result.inserted.size > 0
            }
    }

    override fun insertAnnouncement(error: String, pumpId: Long?, pumpType: PumpType, pumpSerial: String) {
        if (!confirmActivePump(dateUtil.now(), pumpType, pumpSerial)) return
        disposable += repository.runTransaction(InsertTherapyEventAnnouncementTransaction(error, pumpId, pumpType.toDbPumpType(), pumpSerial))
            .subscribe()
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
        val temporaryBasal = TemporaryBasal(
            timestamp = timestamp,
            rate = rate,
            duration = duration,
            type = type?.toDbType() ?: TemporaryBasal.Type.NORMAL,
            isAbsolute = isAbsolute,
            interfaceIDs_backing = InterfaceIDs(
                pumpId = pumpId,
                pumpType = pumpType.toDbPumpType(),
                pumpSerial = pumpSerial
            )
        )
        repository.runTransactionForResult(SyncPumpTemporaryBasalTransaction(temporaryBasal, type?.toDbType()))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while TemporaryBasal", it) }
            .blockingGet()
            .also { result ->
                result.inserted.forEach { aapsLogger.debug(LTag.DATABASE, "Inserted TemporaryBasal $it") }
                result.updated.forEach { aapsLogger.debug(LTag.DATABASE, "Updated TemporaryBasal ${it.first} New: ${it.second}") }
                return result.inserted.size > 0
            }
    }

    override fun syncStopTemporaryBasalWithPumpId(timestamp: Long, endPumpId: Long, pumpType: PumpType, pumpSerial: String): Boolean {
        if (!confirmActivePump(timestamp, pumpType, pumpSerial)) return false
        repository.runTransactionForResult(SyncPumpCancelTemporaryBasalIfAnyTransaction(timestamp, endPumpId, pumpType.toDbPumpType(), pumpSerial))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while saving TemporaryBasal", it) }
            .blockingGet()
            .also { result ->
                result.updated.forEach {
                    aapsLogger.debug(LTag.DATABASE, "Updated TemporaryBasal ${it.first} New: ${it.second}")
                }
                return result.updated.size > 0
            }
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
        val temporaryBasal = TemporaryBasal(
            timestamp = timestamp,
            rate = rate,
            duration = duration,
            type = type.toDbType(),
            isAbsolute = isAbsolute,
            interfaceIDs_backing = InterfaceIDs(
                temporaryId = tempId,
                pumpType = pumpType.toDbPumpType(),
                pumpSerial = pumpSerial
            )
        )
        repository.runTransactionForResult(InsertTemporaryBasalWithTempIdTransaction(temporaryBasal))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while saving TemporaryBasal", it) }
            .blockingGet()
            .also { result ->
                result.inserted.forEach { aapsLogger.debug(LTag.DATABASE, "Inserted TemporaryBasal $it") }
                return result.inserted.size > 0
            }
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
        val bolus = TemporaryBasal(
            timestamp = timestamp,
            rate = rate,
            duration = duration,
            type = TemporaryBasal.Type.NORMAL, // not used for update
            isAbsolute = isAbsolute,
            interfaceIDs_backing = InterfaceIDs(
                temporaryId = temporaryId,
                pumpId = pumpId,
                pumpType = pumpType.toDbPumpType(),
                pumpSerial = pumpSerial
            )
        )
        repository.runTransactionForResult(SyncTemporaryBasalWithTempIdTransaction(bolus, type?.toDbType()))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while saving TemporaryBasal", it) }
            .blockingGet()
            .also { result ->
                result.updated.forEach { aapsLogger.debug(LTag.DATABASE, "Updated TemporaryBasal ${it.first} New: ${it.second}") }
                return result.updated.size > 0
            }
    }

    override fun invalidateTemporaryBasal(id: Long): Boolean {
        repository.runTransactionForResult(InvalidateTemporaryBasalTransaction(id))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while invalidating TemporaryBasal", it) }
            .blockingGet()
            .also { result ->
                result.invalidated.forEach {
                    aapsLogger.debug(LTag.DATABASE, "Invalidated TemporaryBasal $it")
                }
                return result.invalidated.size > 0
            }
    }

    override fun invalidateTemporaryBasalWithPumpId(pumpId: Long, pumpType: PumpType, pumpSerial: String): Boolean {
        repository.runTransactionForResult(
            InvalidateTemporaryBasalTransactionWithPumpId(
                pumpId, pumpType.toDbPumpType(),
                pumpSerial
            )
        )
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while invalidating TemporaryBasal", it) }
            .blockingGet()
            .also { result ->
                result.invalidated.forEach {
                    aapsLogger.debug(LTag.DATABASE, "Invalidated TemporaryBasal $it")
                }
                return result.invalidated.size > 0
            }
    }

    override fun invalidateTemporaryBasalWithTempId(temporaryId: Long): Boolean {
        repository.runTransactionForResult(InvalidateTemporaryBasalWithTempIdTransaction(temporaryId))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while invalidating TemporaryBasal", it) }
            .blockingGet()
            .also { result ->
                result.invalidated.forEach {
                    aapsLogger.debug(LTag.DATABASE, "Invalidated TemporaryBasal $it")
                }
                return result.invalidated.size > 0
            }
    }

    override fun syncExtendedBolusWithPumpId(timestamp: Long, amount: Double, duration: Long, isEmulatingTB: Boolean, pumpId: Long, pumpType: PumpType, pumpSerial: String): Boolean {
        if (!confirmActivePump(timestamp, pumpType, pumpSerial)) return false
        val extendedBolus = ExtendedBolus(
            timestamp = timestamp,
            amount = amount,
            duration = duration,
            isEmulatingTempBasal = isEmulatingTB,
            interfaceIDs_backing = InterfaceIDs(
                pumpId = pumpId,
                pumpType = pumpType.toDbPumpType(),
                pumpSerial = pumpSerial
            )
        )
        repository.runTransactionForResult(SyncPumpExtendedBolusTransaction(extendedBolus))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while ExtendedBolus", it) }
            .blockingGet()
            .also { result ->
                result.inserted.forEach { aapsLogger.debug(LTag.DATABASE, "Inserted ExtendedBolus $it") }
                result.updated.forEach { aapsLogger.debug(LTag.DATABASE, "Updated ExtendedBolus $it") }
                return result.inserted.size > 0
            }
    }

    override fun syncStopExtendedBolusWithPumpId(timestamp: Long, endPumpId: Long, pumpType: PumpType, pumpSerial: String): Boolean {
        if (!confirmActivePump(timestamp, pumpType, pumpSerial)) return false
        repository.runTransactionForResult(SyncPumpCancelExtendedBolusIfAnyTransaction(timestamp, endPumpId, pumpType.toDbPumpType(), pumpSerial))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while saving ExtendedBolus", it) }
            .blockingGet()
            .also { result ->
                result.updated.forEach {
                    aapsLogger.debug(LTag.DATABASE, "Updated ExtendedBolus $it")
                }
                return result.updated.size > 0
            }
    }

    override fun createOrUpdateTotalDailyDose(timestamp: Long, bolusAmount: Double, basalAmount: Double, totalAmount: Double, pumpId: Long?, pumpType: PumpType, pumpSerial: String): Boolean {
        // there are probably old data in pump -> do not show notification, just ignore
        if (!confirmActivePump(timestamp, pumpType, pumpSerial, showNotification = false)) return false
        val tdd = TotalDailyDose(
            timestamp = timestamp,
            bolusAmount = bolusAmount,
            basalAmount = basalAmount,
            totalAmount = totalAmount,
            interfaceIDs_backing = InterfaceIDs(
                pumpId = pumpId,
                pumpType = pumpType.toDbPumpType(),
                pumpSerial = pumpSerial
            )
        )
        repository.runTransactionForResult(SyncPumpTotalDailyDoseTransaction(tdd))
            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while saving TotalDailyDose", it) }
            .blockingGet()
            .also { result ->
                result.inserted.forEach { aapsLogger.debug(LTag.DATABASE, "Inserted TotalDailyDose $it") }
                result.updated.forEach { aapsLogger.debug(LTag.DATABASE, "Updated TotalDailyDose $it") }
                return result.inserted.size > 0
            }
    }
}