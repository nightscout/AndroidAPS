package info.nightscout.androidaps.database.daos.delegated

import info.nightscout.androidaps.database.daos.PreferenceChangeDao
import info.nightscout.androidaps.database.entities.PreferenceChange
import info.nightscout.androidaps.database.interfaces.DBEntry

internal class DelegatedPreferenceChangeDao(changes: MutableList<DBEntry>, private val dao: PreferenceChangeDao) : DelegatedDao(changes), PreferenceChangeDao by dao {

    override fun insert(preferenceChange: PreferenceChange) {
        changes.add(preferenceChange)
        return dao.insert(preferenceChange)
    }

}