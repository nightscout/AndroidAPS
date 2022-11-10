package info.nightscout.database.impl.daos.delegated

import info.nightscout.database.impl.daos.BolusCalculatorResultDao
import info.nightscout.database.entities.BolusCalculatorResult
import info.nightscout.database.entities.interfaces.DBEntry

internal class DelegatedBolusCalculatorResultDao(changes: MutableList<DBEntry>, private val dao: BolusCalculatorResultDao) : DelegatedDao(changes), BolusCalculatorResultDao by dao {

    override fun insertNewEntry(entry: BolusCalculatorResult): Long {
        changes.add(entry)
        return dao.insertNewEntry(entry)
    }

    override fun updateExistingEntry(entry: BolusCalculatorResult): Long {
        changes.add(entry)
        return dao.updateExistingEntry(entry)
    }
}