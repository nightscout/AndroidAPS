package info.nightscout.database.impl.transactions

import app.aaps.database.entities.TherapyEvent

class InsertIfNewByTimestampTherapyEventTransaction(
    val therapyEvent: TherapyEvent
) : Transaction<InsertIfNewByTimestampTherapyEventTransaction.TransactionResult>() {

    constructor(
        timestamp: Long,
        type: TherapyEvent.Type,
        duration: Long = 0,
        note: String? = null,
        enteredBy: String? = null,
        glucose: Double? = null,
        glucoseType: TherapyEvent.MeterType? = null,
        glucoseUnit: TherapyEvent.GlucoseUnit
    ) :
        this(TherapyEvent(timestamp = timestamp, type = type, duration = duration, note = note, enteredBy = enteredBy, glucose = glucose, glucoseType = glucoseType, glucoseUnit = glucoseUnit))

    override fun run(): TransactionResult {
        val result = TransactionResult()
        val current = database.therapyEventDao.findByTimestamp(therapyEvent.type, therapyEvent.timestamp)
        if (current == null) {
            database.therapyEventDao.insertNewEntry(therapyEvent)
            result.inserted.add(therapyEvent)
        } else result.existing.add(therapyEvent)
        return result
    }

    class TransactionResult {

        val inserted = mutableListOf<TherapyEvent>()
        val existing = mutableListOf<TherapyEvent>()
    }
}