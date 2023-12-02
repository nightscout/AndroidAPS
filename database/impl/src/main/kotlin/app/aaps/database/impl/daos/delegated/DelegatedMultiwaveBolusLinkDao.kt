package app.aaps.database.impl.daos.delegated

import app.aaps.database.entities.MultiwaveBolusLink
import app.aaps.database.entities.interfaces.DBEntry
import app.aaps.database.impl.daos.MultiwaveBolusLinkDao

internal class DelegatedMultiwaveBolusLinkDao(changes: MutableList<DBEntry>, private val dao: MultiwaveBolusLinkDao) : DelegatedDao(changes), MultiwaveBolusLinkDao by dao {

    override fun insertNewEntry(entry: MultiwaveBolusLink): Long {
        changes.add(entry)
        return dao.insertNewEntry(entry)
    }

    override fun updateExistingEntry(entry: MultiwaveBolusLink): Long {
        changes.add(entry)
        return dao.updateExistingEntry(entry)
    }
}