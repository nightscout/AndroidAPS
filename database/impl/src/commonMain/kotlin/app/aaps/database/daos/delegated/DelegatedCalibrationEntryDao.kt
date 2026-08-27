package app.aaps.database.daos.delegated

import app.aaps.database.daos.CalibrationEntryDao
import app.aaps.database.entities.CalibrationEntry
import app.aaps.database.entities.interfaces.DBEntry

internal class DelegatedCalibrationEntryDao(changes: MutableList<DBEntry>, private val dao: CalibrationEntryDao) : DelegatedDao(changes), CalibrationEntryDao by dao {

    override fun insertNewEntry(entry: CalibrationEntry): Long {
        changes.add(entry)
        return dao.insertNewEntry(entry)
    }

    override fun updateExistingEntry(entry: CalibrationEntry): Long {
        changes.add(entry)
        return dao.updateExistingEntry(entry)
    }
}
