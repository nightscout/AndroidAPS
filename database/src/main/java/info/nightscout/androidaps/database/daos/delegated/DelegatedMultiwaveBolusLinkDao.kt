package info.nightscout.androidaps.database.daos.delegated

import info.nightscout.androidaps.database.daos.MultiwaveBolusLinkDao
import info.nightscout.androidaps.database.entities.MultiwaveBolusLink
import info.nightscout.androidaps.database.interfaces.DBEntry

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