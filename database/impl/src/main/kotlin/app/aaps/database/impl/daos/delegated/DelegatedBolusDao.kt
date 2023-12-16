package app.aaps.database.impl.daos.delegated

import app.aaps.database.entities.Bolus
import app.aaps.database.entities.interfaces.DBEntry
import app.aaps.database.impl.daos.BolusDao

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