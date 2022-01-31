package info.nightscout.androidaps.database.daos.delegated

import info.nightscout.androidaps.database.daos.BolusCalculatorResultDao
import info.nightscout.androidaps.database.entities.BolusCalculatorResult
import info.nightscout.androidaps.database.interfaces.DBEntry

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