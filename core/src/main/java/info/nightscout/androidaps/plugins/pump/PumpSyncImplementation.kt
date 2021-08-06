package info.nightscout.androidaps.plugins.pump

import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.data.DetailedBolusInfo
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.ValueWrapper
import info.nightscout.androidaps.database.embedments.InterfaceIDs
import info.nightscout.androidaps.database.entities.*
import info.nightscout.androidaps.database.transactions.*
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.interfaces.PumpSync
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import javax.inject.Inject

class PumpSyncImplementation @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val dateUtil: DateUtil,
    private val sp: SP,
    private val rxBus: RxBusWrapper,
    private val resourceHelper: ResourceHelper,
    private val profileFunction: ProfileFunction,
    private val repository: AppRepository
) : PumpSync {

    private val disposable = CompositeDisposable()

    override fun connectNewPump() {
        sp.remove(R.string.key_active_pump_type)
        sp.remove(R.string.key_active_pump_serial_number)
        sp.remove(R.string.key_active_pump_change_timestamp)
    }

    /**
     * Check if data is coming from currently active pump to prevent overlapping pump histories
     *
     * @param timestamp     timestamp of data coming from pump
     * @param type          timestamp of of pump
     * @param serialNumber  serial number  of of pump
     * @return true if data is allowed
     */
    private fun confirmActivePump(timestamp: Long, type: PumpType, serialNumber: String): Boolean {
        val storedType = sp.getString(R.string.key_active_pump_type, "")
        val storedSerial = sp.getString(R.string.key_active_pump_serial_number, "")
        val storedTimestamp = sp.getLong(R.string.key_active_pump_change_timestamp, 0L)

        // If no value stored assume we start using new pump from now
        if (storedType.isEmpty() || storedSerial.isEmpty()) {
            aapsLogger.debug(LTag.PUMP, "Registering new pump ${type.description} $serialNumber")
            sp.putString(R.string.key_active_pump_type, type.description)
            sp.putString(R.string.key_active_pump_serial_number, serialNumber)
            sp.putLong(R.string.key_active_pump_change_timestamp, dateUtil.now()) // allow only data newer than register time (ie. ignore older history)
            return timestamp > dateUtil.now() - T.mins(1).msecs() // allow first record to be 1 min old
        }

        if (type.description == storedType && serialNumber == storedSerial && timestamp >= storedTimestamp) {
            // data match
            return true
        }

        if ((type.description != storedType || serialNumber != storedSerial) && timestamp >= storedTimestamp)
            rxBus.send(EventNewNotification(Notification(Notification.WRONG_PUMP_DATA, resourceHelper.gs(R.string.wrong_pump_data), Notification.URGENT)))
        aapsLogger.error(LTag.PUMP, "Ignoring pump history record  Allowed: ${dateUtil.dateAndTimeAndSecondsString(storedTimestamp)} $storedType $storedSerial Received: $timestamp ${dateUtil.dateAndTimeAndSecondsString(timestamp)} ${type.description} $serialNumber")
        return false
    }

    override fun expectedPumpState(): PumpSync.PumpState {
        val bolus = repository.getLastBolusRecord()
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
                    pumpId = temporaryBasal.value.interfaceIDs.pumpId
                )
            else null,
            extendedBolus =
            if (extendedBolus is ValueWrapper.Existing)
                PumpSync.PumpState.ExtendedBolus(
                    timestamp = extendedBolus.value.timestamp,
                    duration = extendedBolus.value.duration,
                    amount = extendedBolus.value.amount,
                    rate = extendedBolus.value.rate
                )
            else null,
            bolus =
            bolus?.let {
                PumpSync.PumpState.Bolus(
                    timestamp = bolus.timestamp,
                    amount = bolus.amount
                )
            },
            profile = profileFunction.getProfile()
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
                pumpSerial = pumpSerial)
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
                pumpSerial = pumpSerial)
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

    override fun syncTemporaryBasalWithPumpId(timestamp: Long, rate: Double, duration: Long, isAbsolute: Boolean, type: PumpSync.TemporaryBasalType?, pumpId: Long, pumpType: PumpType, pumpSerial: String): Boolean {
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
                result.updated.forEach { aapsLogger.debug(LTag.DATABASE, "Updated TemporaryBasal $it") }
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
                    aapsLogger.debug(LTag.DATABASE, "Updated TemporaryBasal $it")
                }
                return result.updated.size > 0
            }
    }

    override fun addTemporaryBasalWithTempId(timestamp: Long, rate: Double, duration: Long, isAbsolute: Boolean, tempId: Long, type: PumpSync.TemporaryBasalType, pumpType: PumpType, pumpSerial: String): Boolean {
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

    override fun syncTemporaryBasalWithTempId(timestamp: Long, rate: Double, duration: Long, isAbsolute: Boolean, temporaryId: Long, type: PumpSync.TemporaryBasalType?, pumpId: Long?, pumpType: PumpType, pumpSerial: String): Boolean {
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
                result.updated.forEach { aapsLogger.debug(LTag.DATABASE, "Updated TemporaryBasal $it") }
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
        repository.runTransactionForResult(InvalidateTemporaryBasalTransactionWithPumpId(pumpId, pumpType.toDbPumpType(),
                                                                                         pumpSerial))
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
        if (!confirmActivePump(timestamp, pumpType, pumpSerial)) return false
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