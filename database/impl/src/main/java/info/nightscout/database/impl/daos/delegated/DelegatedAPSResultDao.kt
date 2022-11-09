package info.nightscout.database.impl.daos.delegated

import info.nightscout.database.entities.APSResult
import info.nightscout.database.entities.interfaces.DBEntry
import info.nightscout.database.impl.daos.APSResultDao

internal class DelegatedAPSResultDao(changes: MutableList<DBEntry>, private val dao: APSResultDao) : DelegatedDao(changes), APSResultDao by dao {

    override fun insertNewEntry(entry: APSResult): Long {
        changes.add(entry)
        return dao.insertNewEntry(entry)
    }

    override fun updateExistingEntry(entry: APSResult): Long {
        changes.add(entry)
        return dao.updateExistingEntry(entry)
    }
}