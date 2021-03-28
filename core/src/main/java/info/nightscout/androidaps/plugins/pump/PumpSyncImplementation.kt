package info.nightscout.androidaps.plugins.pump

import info.nightscout.androidaps.data.DetailedBolusInfo
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.embedments.InterfaceIDs
import info.nightscout.androidaps.database.entities.Bolus
import info.nightscout.androidaps.database.entities.Carbs
import info.nightscout.androidaps.database.entities.TherapyEvent
import info.nightscout.androidaps.database.transactions.InsertIfNewByTimestampCarbsTransaction
import info.nightscout.androidaps.database.transactions.InsertIfNewByTimestampTherapyEventTransaction
import info.nightscout.androidaps.database.transactions.InsertTherapyEventAnnouncementTransaction
import info.nightscout.androidaps.database.transactions.SyncPumpBolusTransaction
import info.nightscout.androidaps.interfaces.PumpSync
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import javax.inject.Inject

class PumpSyncImplementation @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val repository: AppRepository
) : PumpSync {

    private val disposable = CompositeDisposable()

    override fun addBolusWithTempId(timestamp: Long, amount: Double, driverId: Long, pumpType: PumpType, pumpSerial: String) {
        TODO("Not yet implemented")
    }

    override fun syncBolusWithTempId(timestamp: Long, amount: Double, driverId: Long, pumpId: Long?, pumpType: PumpType, pumpSerial: String) {
        TODO("Not yet implemented")
    }

    override fun syncBolusWithPumpId(timestamp: Long, amount: Double, type: DetailedBolusInfo.BolusType, pumpId: Long, pumpType: PumpType, pumpSerial: String) {
        val bolus = Bolus(
            timestamp = timestamp,
            amount = amount,
            type = type.toDBbBolusType(),
            interfaceIDs_backing = InterfaceIDs(
                pumpId = pumpId,
                pumpType = pumpType.toDbPumpType(),
                pumpSerial = pumpSerial
            ),
            isBasalInsulin = false
        )
        disposable += repository.runTransactionForResult(SyncPumpBolusTransaction(bolus))
            .subscribe(
                { result ->
                    result.inserted.forEach { aapsLogger.debug(LTag.DATABASE, "Inserted carbs $it") }
                    result.updated.forEach { aapsLogger.debug(LTag.DATABASE, "Updated carbs $it") }
                },
                { aapsLogger.error(LTag.DATABASE, "Error while saving carbs", it) }
            )
    }

    override fun syncCarbsWithTimestamp(timestamp: Long, amount: Double, pumpId: Long?, pumpType: PumpType, pumpSerial: String) {
        val carbs = Carbs(
            timestamp = timestamp,
            amount = amount,
            duration = 0,
            interfaceIDs_backing = InterfaceIDs(
                pumpId = pumpId,
                pumpType = pumpType.toDbPumpType(),
                pumpSerial = pumpSerial)
        )
        disposable += repository.runTransactionForResult(InsertIfNewByTimestampCarbsTransaction(carbs))
            .subscribe(
                { result -> result.inserted.forEach { aapsLogger.debug(LTag.DATABASE, "Inserted carbs $it") } },
                { aapsLogger.error(LTag.DATABASE, "Error while saving carbs", it) }
            )
    }

    override fun insertTherapyEventIfNewWithTimestamp(timestamp: Long, type: DetailedBolusInfo.EventType, note: String?, pumpId: Long?, pumpType: PumpType, pumpSerial: String) {
        val therapyEvent = TherapyEvent(
            timestamp = timestamp,
            type = type.toDBbEventType(),
            duration = 0,
            note = null,
            enteredBy = "AndroidAPS",
            glucose = null,
            glucoseType = null,
            glucoseUnit = TherapyEvent.GlucoseUnit.MGDL,
            interfaceIDs_backing = InterfaceIDs(
                pumpId = pumpId,
                pumpType = pumpType.toDbPumpType(),
                pumpSerial = pumpSerial)
        )
        disposable += repository.runTransactionForResult(InsertIfNewByTimestampTherapyEventTransaction(therapyEvent))
            .subscribe(
                { result -> result.inserted.forEach { aapsLogger.debug(LTag.DATABASE, "Inserted therapy event $it") } },
                { aapsLogger.error(LTag.DATABASE, "Error while saving therapy event", it) }
            )
    }

    override fun insertAnnouncement(error: String, pumpId: Long?, pumpType: PumpType, pumpSerial: String) {
        disposable += repository.runTransaction(InsertTherapyEventAnnouncementTransaction(error, pumpId, pumpType.toDbPumpType(), pumpSerial))
            .subscribe()
    }

}