package info.nightscout.androidaps.database.daos.delegated

import info.nightscout.androidaps.database.daos.APSResultDao
import info.nightscout.androidaps.database.entities.APSResult
import info.nightscout.androidaps.database.interfaces.DBEntry

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