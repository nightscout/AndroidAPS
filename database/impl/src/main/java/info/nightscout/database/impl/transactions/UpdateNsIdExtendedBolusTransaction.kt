package info.nightscout.database.impl.transactions

import info.nightscout.database.entities.ExtendedBolus

class UpdateNsIdExtendedBolusTransaction(val boluses: List<ExtendedBolus>) : Transaction<UpdateNsIdExtendedBolusTransaction.TransactionResult>() {

    val result = TransactionResult()
    override fun run(): TransactionResult {
        for (bolus in boluses) {
            val current = database.extendedBolusDao.findById(bolus.id)
            if (current != null && current.interfaceIDs.nightscoutId != bolus.interfaceIDs.nightscoutId) {
                current.interfaceIDs.nightscoutId = bolus.interfaceIDs.nightscoutId
                database.extendedBolusDao.updateExistingEntry(current)
                result.updatedNsId.add(current)
            }
        }
        return result
    }

    class TransactionResult {

        val updatedNsId = mutableListOf<ExtendedBolus>()
    }
}