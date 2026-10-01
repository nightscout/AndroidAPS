package app.aaps.database.daos.delegated

import app.aaps.database.daos.BolusCalculatorResultDao
import app.aaps.database.entities.BolusCalculatorResult
import app.aaps.database.entities.interfaces.DBEntry

internal class DelegatedBolusCalculatorResultDao(changes: MutableList<DBEntry>, private val dao: BolusCalculatorResultDao) : DelegatedDao(changes), BolusCalculatorResultDao by dao {

    override suspend fun insertNewEntry(entry: BolusCalculatorResult): Long {
        changes.add(entry)
        return dao.insertNewEntry(entry)
    }

    override suspend fun updateExistingEntry(entry: BolusCalculatorResult): Long {
        changes.add(entry)
        return dao.updateExistingEntry(entry)
    }
}