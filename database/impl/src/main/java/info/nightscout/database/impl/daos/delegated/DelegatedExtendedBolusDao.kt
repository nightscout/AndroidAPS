package info.nightscout.database.impl.daos.delegated

import info.nightscout.database.impl.daos.ExtendedBolusDao
import info.nightscout.database.entities.ExtendedBolus
import info.nightscout.database.entities.interfaces.DBEntry

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