package info.nightscout.androidaps.database.daos.delegated

import info.nightscout.androidaps.database.daos.BolusDao
import info.nightscout.androidaps.database.entities.Bolus
import info.nightscout.androidaps.database.interfaces.DBEntry

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