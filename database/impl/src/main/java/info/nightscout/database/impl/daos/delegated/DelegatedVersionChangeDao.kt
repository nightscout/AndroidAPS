package info.nightscout.database.impl.daos.delegated

import info.nightscout.database.impl.daos.VersionChangeDao
import info.nightscout.database.entities.VersionChange
import info.nightscout.database.entities.interfaces.DBEntry

internal class DelegatedVersionChangeDao(changes: MutableList<DBEntry>, private val dao: VersionChangeDao) : DelegatedDao(changes), VersionChangeDao by dao {

    override fun insert(versionChange: VersionChange) {
        changes.add(versionChange)
        return dao.insert(versionChange)
    }

}