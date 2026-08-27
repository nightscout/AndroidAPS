package app.aaps.database.daos.delegated

import app.aaps.database.daos.ExtendedBolusDao
import app.aaps.database.entities.ExtendedBolus
import app.aaps.database.entities.interfaces.DBEntry

internal class DelegatedExtendedBolusDao(changes: MutableList<DBEntry>, private val dao: ExtendedBolusDao) : DelegatedDao(changes), ExtendedBolusDao by dao {

    override fun insertNewEntry(entry: ExtendedBolus): Long {
        changes.add(entry)
        return dao.insertNewEntry(entry)
    }

    override fun updateExistingEntry(entry: ExtendedBolus): Long {
        changes.add(entry)
        return dao.updateExistingEntry(entry)
    }
}