package app.aaps.database.impl.daos.delegated

import app.aaps.database.entities.interfaces.DBEntry
import app.aaps.database.impl.daos.APSResultLinkDao

internal class DelegatedAPSResultLinkDao(changes: MutableList<DBEntry>, private val dao: APSResultLinkDao) : DelegatedDao(changes), APSResultLinkDao by dao {

    override fun insertNewEntry(entry: app.aaps.database.entities.APSResultLink): Long {
        changes.add(entry)
        return dao.insertNewEntry(entry)
    }

    override fun updateExistingEntry(entry: app.aaps.database.entities.APSResultLink): Long {
        changes.add(entry)
        return dao.updateExistingEntry(entry)
    }
}