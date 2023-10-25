package app.aaps.database.impl.daos.delegated

import app.aaps.database.entities.HeartRate
import app.aaps.database.entities.interfaces.DBEntry
import app.aaps.database.impl.daos.HeartRateDao

internal class DelegatedHeartRateDao(
    changes: MutableList<DBEntry>,
    private val dao: HeartRateDao
) : DelegatedDao(changes), HeartRateDao by dao {

    override fun insertNewEntry(entry: HeartRate): Long {
        changes.add(entry)
        return dao.insertNewEntry(entry)
    }

    override fun updateExistingEntry(entry: HeartRate): Long {
        changes.add(entry)
        return dao.updateExistingEntry(entry)
    }
}
