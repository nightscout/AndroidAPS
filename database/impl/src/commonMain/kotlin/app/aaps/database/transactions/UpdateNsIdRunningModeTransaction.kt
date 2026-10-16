package app.aaps.database.transactions

import app.aaps.database.entities.RunningMode

class UpdateNsIdRunningModeTransaction(val runningModes: List<RunningMode>) : Transaction<UpdateNsIdRunningModeTransaction.TransactionResult>() {

    val result = TransactionResult()
    override suspend fun run(): TransactionResult {
        for (runningMode in runningModes) {
            val current = database.runningModeDao.findById(runningMode.id)
            if (current != null && current.interfaceIDs.nightscoutId != runningMode.interfaceIDs.nightscoutId) {
                current.interfaceIDs.nightscoutId = runningMode.interfaceIDs.nightscoutId
                database.runningModeDao.updateExistingEntry(current)
                result.updatedNsId.add(current)
            }
        }
        return result
    }

    class TransactionResult {

        val updatedNsId = mutableListOf<RunningMode>()
    }
}