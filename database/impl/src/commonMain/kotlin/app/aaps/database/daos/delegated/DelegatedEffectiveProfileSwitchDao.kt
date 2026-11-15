package app.aaps.database.daos.delegated

import app.aaps.database.daos.EffectiveProfileSwitchDao
import app.aaps.database.entities.EffectiveProfileSwitch
import app.aaps.database.entities.interfaces.DBEntry

internal class DelegatedEffectiveProfileSwitchDao(changes: MutableList<DBEntry>, private val dao: EffectiveProfileSwitchDao) : DelegatedDao(changes), EffectiveProfileSwitchDao by dao {

    override suspend fun insertNewEntry(entry: EffectiveProfileSwitch): Long {
        changes.add(entry)
        return super.insertNewEntry(entry)
    }

    override suspend fun updateExistingEntry(entry: EffectiveProfileSwitch): Long {
        changes.add(entry)
        return super.updateExistingEntry(entry)
    }
}