package info.nightscout.androidaps.database.daos.delegated

import info.nightscout.androidaps.database.daos.MealLinkDao
import info.nightscout.androidaps.database.entities.MealLink
import info.nightscout.androidaps.database.interfaces.DBEntry

internal class DelegatedMealLinkDao(changes: MutableList<DBEntry>, private val dao: MealLinkDao) : DelegatedDao(changes), MealLinkDao by dao {

    override fun insertNewEntry(entry: MealLink): Long {
        changes.add(entry)
        return dao.insertNewEntry(entry)
    }

    override fun updateExistingEntry(entry: MealLink): Long {
        changes.add(entry)
        return dao.updateExistingEntry(entry)
    }
}