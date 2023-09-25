package info.nightscout.database.impl.transactions

import app.aaps.database.entities.TherapyEvent
import app.aaps.database.entities.embedments.InterfaceIDs

class InsertTherapyEventAnnouncementTransaction(
    val therapyEvent: TherapyEvent
) : Transaction<InsertTherapyEventAnnouncementTransaction.TransactionResult>() {

    constructor(error: String, pumpId: Long? = null, pumpType: InterfaceIDs.PumpType? = null, pumpSerial: String? = null) :
        this(
            TherapyEvent(
                timestamp = System.currentTimeMillis(),
                type = TherapyEvent.Type.ANNOUNCEMENT,
                duration = 0, note = error,
                enteredBy = "AAPS",
                glucose = null,
                glucoseType = null,
                glucoseUnit = TherapyEvent.GlucoseUnit.MGDL,
                interfaceIDs_backing = InterfaceIDs(
                    pumpId = pumpId,
                    pumpType = pumpType,
                    pumpSerial = pumpSerial
                )
            )
        )

    override fun run(): TransactionResult {
        val result = TransactionResult()
        database.therapyEventDao.insertNewEntry(therapyEvent)
        result.inserted.add(therapyEvent)
        return result
    }

    class TransactionResult {

        val inserted = mutableListOf<TherapyEvent>()
    }
}