package info.nightscout.androidaps.database.transactions

import info.nightscout.androidaps.database.entities.TherapyEvent

class InsertTherapyEventAnnouncementTransaction(
    val therapyEvent: TherapyEvent
) : Transaction<InsertTherapyEventAnnouncementTransaction.TransactionResult>() {

    constructor(error: String) :
        this(TherapyEvent(timestamp = System.currentTimeMillis(), type = TherapyEvent.Type.ANNOUNCEMENT, duration = 0, note = error, enteredBy = "AndroidAPS", glucose = null, glucoseType = null, glucoseUnit = TherapyEvent.GlucoseUnit.MGDL))

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