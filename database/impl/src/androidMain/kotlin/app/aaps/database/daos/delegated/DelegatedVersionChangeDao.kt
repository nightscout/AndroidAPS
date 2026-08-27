package app.aaps.database.daos.delegated

import app.aaps.database.daos.VersionChangeDao
import app.aaps.database.entities.VersionChange
import app.aaps.database.entities.interfaces.DBEntry

internal class DelegatedVersionChangeDao(changes: MutableList<DBEntry>, private val dao: VersionChangeDao) : DelegatedDao(changes), VersionChangeDao by dao {

    override fun insert(versionChange: VersionChange) {
        changes.add(versionChange)
        return dao.insert(versionChange)
    }

}