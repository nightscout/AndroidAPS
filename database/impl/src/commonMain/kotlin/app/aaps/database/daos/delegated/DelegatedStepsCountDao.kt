package app.aaps.database.daos.delegated

import app.aaps.database.daos.StepsCountDao
import app.aaps.database.entities.StepsCount
import app.aaps.database.entities.interfaces.DBEntry

internal class DelegatedStepsCountDao(
    changes: MutableList<DBEntry>,
    private val dao: StepsCountDao
) : DelegatedDao(changes), StepsCountDao by dao {

    override fun insertNewEntry(entry: StepsCount): Long {
        changes.add(entry)
        return dao.insertNewEntry(entry)
    }

    override fun updateExistingEntry(entry: StepsCount): Long {
        changes.add(entry)
        return dao.updateExistingEntry(entry)
    }
}
