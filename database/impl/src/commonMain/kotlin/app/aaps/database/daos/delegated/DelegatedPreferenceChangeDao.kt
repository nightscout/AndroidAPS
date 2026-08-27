package app.aaps.database.daos.delegated

import app.aaps.database.daos.PreferenceChangeDao
import app.aaps.database.entities.PreferenceChange
import app.aaps.database.entities.interfaces.DBEntry

internal class DelegatedPreferenceChangeDao(changes: MutableList<DBEntry>, private val dao: PreferenceChangeDao) : DelegatedDao(changes), PreferenceChangeDao by dao {

    override fun insert(preferenceChange: PreferenceChange) {
        changes.add(preferenceChange)
        return dao.insert(preferenceChange)
    }

}