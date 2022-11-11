package info.nightscout.database.impl.daos.delegated

import info.nightscout.database.impl.daos.BolusDao
import info.nightscout.database.entities.Bolus
import info.nightscout.database.entities.interfaces.DBEntry

internal class DelegatedBolusDao(changes: MutableList<DBEntry>, private val dao: BolusDao) : DelegatedDao(changes), BolusDao by dao {

    override fun insertNewEntry(entry: Bolus): Long {
        changes.add(entry)
        return dao.insertNewEntry(entry)
    }

    override fun updateExistingEntry(entry: Bolus): Long {
        changes.add(entry)
        return dao.updateExistingEntry(entry)
    }
}