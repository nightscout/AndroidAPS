package info.nightscout.androidaps.database.daos.delegated

import info.nightscout.androidaps.database.daos.APSResultLinkDao
import info.nightscout.androidaps.database.entities.APSResultLink
import info.nightscout.androidaps.database.interfaces.DBEntry

internal class DelegatedAPSResultLinkLinkDao(changes: MutableList<DBEntry>, private val dao: APSResultLinkDao) : DelegatedDao(changes), APSResultLinkDao by dao {

    override fun insertNewEntry(entry: APSResultLink): Long {
        changes.add(entry)
        return dao.insertNewEntry(entry)
    }

    override fun updateExistingEntry(entry: APSResultLink): Long {
        changes.add(entry)
        return dao.updateExistingEntry(entry)
    }
}