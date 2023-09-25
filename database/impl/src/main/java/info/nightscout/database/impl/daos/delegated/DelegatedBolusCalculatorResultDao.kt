package info.nightscout.database.impl.daos.delegated

import app.aaps.database.entities.BolusCalculatorResult
import app.aaps.database.entities.interfaces.DBEntry
import info.nightscout.database.impl.daos.BolusCalculatorResultDao

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