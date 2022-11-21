package info.nightscout.database.impl.daos.delegated

import info.nightscout.database.impl.daos.APSResultLinkDao
import info.nightscout.database.entities.APSResultLink
import info.nightscout.database.entities.interfaces.DBEntry

internal class DelegatedAPSResultLinkDao(changes: MutableList<DBEntry>, private val dao: APSResultLinkDao) : DelegatedDao(changes), APSResultLinkDao by dao {

    override fun insertNewEntry(entry: APSResultLink): Long {
        changes.add(entry)
        return dao.insertNewEntry(entry)
    }

    override fun updateExistingEntry(entry: APSResultLink): Long {
        changes.add(entry)
        return dao.updateExistingEntry(entry)
    }
}