package app.aaps.database.transactions

import app.aaps.database.entities.Bolus
import app.aaps.database.entities.TemporaryBasal
import app.aaps.database.entities.embedments.InterfaceIDs

/**
 * Keeps the temp basals and boluses that AAPS set and that are not found in the pump history yet, as they were
 * set: each is stored again, with its temporary id as pump id, and the entry with only the temporary id is
 * invalidated.
 *
 * For a pump change. The old pump does not confirm them any more, and the history of a pump that comes back is
 * taken only from its new registration. Before, they kept only the temporary id for good, and a record without a
 * pump id is never uploaded to Nightscout.
 *
 * A new entry, not an update: the Nightscout sync sends the entries with a pump id that are newer than the last one
 * it sent. These entries are older than that, and an update stores their old version, still without a pump id, as
 * the newer entry, so the sync never sees them. The invalidation removes nothing from Nightscout: an entry without
 * a pump id was never sent. The temporary id is not copied, so a search by it finds only the old entry.
 */
class ConfirmTemporaryIdsTransaction : Transaction<ConfirmTemporaryIdsTransaction.TransactionResult>() {

    override suspend fun run(): TransactionResult {
        val result = TransactionResult()
        database.temporaryBasalDao.getWithTemporaryIdOnly().forEach { temporaryBasal ->
            val kept = temporaryBasal.copy(id = 0, version = 0, interfaceIDs_backing = temporaryBasal.interfaceIDs.confirmed())
            temporaryBasal.isValid = false
            database.temporaryBasalDao.updateExistingEntry(temporaryBasal)
            database.temporaryBasalDao.insertNewEntry(kept)
            result.temporaryBasals.add(kept)
        }
        database.bolusDao.getWithTemporaryIdOnly().forEach { bolus ->
            val kept = bolus.copy(id = 0, version = 0, interfaceIDs_backing = bolus.interfaceIDs.confirmed())
            bolus.isValid = false
            database.bolusDao.updateExistingEntry(bolus)
            database.bolusDao.insertNewEntry(kept)
            result.boluses.add(kept)
        }
        return result
    }

    private fun InterfaceIDs.confirmed() = copy(pumpId = temporaryId, temporaryId = null)

    class TransactionResult {

        val temporaryBasals = mutableListOf<TemporaryBasal>()
        val boluses = mutableListOf<Bolus>()
    }
}
