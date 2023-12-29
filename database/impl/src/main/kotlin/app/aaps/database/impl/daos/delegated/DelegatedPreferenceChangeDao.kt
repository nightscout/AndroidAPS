package app.aaps.database.impl.daos.delegated

import app.aaps.database.entities.PreferenceChange
import app.aaps.database.entities.interfaces.DBEntry
import app.aaps.database.impl.daos.PreferenceChangeDao

internal class DelegatedPreferenceChangeDao(changes: MutableList<DBEntry>, private val dao: PreferenceChangeDao) : DelegatedDao(changes), PreferenceChangeDao by dao {

    override fun insert(preferenceChange: PreferenceChange) {
        changes.add(preferenceChange)
        return dao.insert(preferenceChange)
    }

}