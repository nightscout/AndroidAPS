package app.aaps.database.daos.delegated

import app.aaps.database.daos.RunningModeDao
import app.aaps.database.entities.RunningMode
import app.aaps.database.entities.interfaces.DBEntry

internal class DelegatedRunningModeDao(changes: MutableList<DBEntry>, private val dao: RunningModeDao) : DelegatedDao(changes), RunningModeDao by dao {

    override fun insertNewEntry(entry: RunningMode): Long {
        changes.add(entry)
        return super.insertNewEntry(entry)
    }

    override fun updateExistingEntry(entry: RunningMode): Long {
        changes.add(entry)
        return super.updateExistingEntry(entry)
    }
}