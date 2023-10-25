package app.aaps.database.impl.daos.delegated

import app.aaps.database.entities.ExtendedBolus
import app.aaps.database.entities.interfaces.DBEntry
import app.aaps.database.impl.daos.ExtendedBolusDao

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