package info.nightscout.database.impl.daos.delegated

import info.nightscout.database.impl.daos.MultiwaveBolusLinkDao
import info.nightscout.database.entities.MultiwaveBolusLink
import info.nightscout.database.entities.interfaces.DBEntry

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