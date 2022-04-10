package info.nightscout.androidaps.database.daos.delegated

import info.nightscout.androidaps.database.daos.VersionChangeDao
import info.nightscout.androidaps.database.entities.VersionChange
import info.nightscout.androidaps.database.interfaces.DBEntry

internal class DelegatedVersionChangeDao(changes: MutableList<DBEntry>, private val dao: VersionChangeDao) : DelegatedDao(changes), VersionChangeDao by dao {

    override fun insert(versionChange: VersionChange) {
        changes.add(versionChange)
        return dao.insert(versionChange)
    }

}