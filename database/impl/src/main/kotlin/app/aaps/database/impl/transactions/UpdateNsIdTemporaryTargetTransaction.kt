package app.aaps.database.impl.transactions

import app.aaps.database.entities.TemporaryTarget

class UpdateNsIdTemporaryTargetTransaction(private val temporaryTargets: List<TemporaryTarget>) : Transaction<UpdateNsIdTemporaryTargetTransaction.TransactionResult>() {

    val result = TransactionResult()
    override fun run(): TransactionResult {
        for (temporaryTarget in temporaryTargets) {
            val current = database.temporaryTargetDao.findById(temporaryTarget.id)
            if (current != null && current.interfaceIDs.nightscoutId != temporaryTarget.interfaceIDs.nightscoutId) {
                current.interfaceIDs.nightscoutId = temporaryTarget.interfaceIDs.nightscoutId
                database.temporaryTargetDao.updateExistingEntry(current)
                result.updatedNsId.add(current)
            }
        }
        return result
    }

    class TransactionResult {

        val updatedNsId = mutableListOf<TemporaryTarget>()
    }
}