package info.nightscout.androidaps.database.daos.delegated

import info.nightscout.androidaps.database.daos.ExtendedBolusDao
import info.nightscout.androidaps.database.entities.ExtendedBolus
import info.nightscout.androidaps.database.interfaces.DBEntry

internal class DelegatedExtendedExtendedBolusDao(changes: MutableList<DBEntry>, private val dao: ExtendedBolusDao) : DelegatedDao(changes), ExtendedBolusDao by dao {

    override fun insertNewEntry(entry: ExtendedBolus): Long {
        changes.add(entry)
        return dao.insertNewEntry(entry)
    }

    override fun updateExistingEntry(entry: ExtendedBolus): Long {
        changes.add(entry)
        return dao.updateExistingEntry(entry)
    }
}