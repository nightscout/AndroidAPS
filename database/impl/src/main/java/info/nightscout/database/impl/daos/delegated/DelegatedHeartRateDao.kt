package info.nightscout.database.impl.daos.delegated

import info.nightscout.database.entities.HeartRate
import info.nightscout.database.entities.interfaces.DBEntry
import info.nightscout.database.impl.daos.HeartRateDao

internal class DelegatedHeartRateDao(
    changes: MutableList<DBEntry>,
    private val dao:HeartRateDao): DelegatedDao(changes), HeartRateDao by dao {

    override fun insertNewEntry(entry: HeartRate): Long {
        changes.add(entry)
        return dao.insertNewEntry(entry)
    }

    override fun updateExistingEntry(entry: HeartRate): Long {
        changes.add(entry)
        return dao.updateExistingEntry(entry)
    }
}
