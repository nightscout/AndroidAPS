package info.nightscout.database.impl.daos.delegated

import info.nightscout.database.impl.daos.PreferenceChangeDao
import info.nightscout.database.entities.PreferenceChange
import info.nightscout.database.entities.interfaces.DBEntry

internal class DelegatedPreferenceChangeDao(changes: MutableList<DBEntry>, private val dao: PreferenceChangeDao) : DelegatedDao(changes), PreferenceChangeDao by dao {

    override fun insert(preferenceChange: PreferenceChange) {
        changes.add(preferenceChange)
        return dao.insert(preferenceChange)
    }

}