package info.nightscout.database.impl.daos.delegated

import info.nightscout.database.impl.daos.EffectiveProfileSwitchDao
import info.nightscout.database.entities.EffectiveProfileSwitch
import info.nightscout.database.entities.interfaces.DBEntry

internal class DelegatedEffectiveProfileSwitchDao(changes: MutableList<DBEntry>, private val dao: EffectiveProfileSwitchDao) : DelegatedDao(changes), EffectiveProfileSwitchDao by dao {

    override fun insertNewEntry(entry: EffectiveProfileSwitch): Long {
        changes.add(entry)
        return super.insertNewEntry(entry)
    }

    override fun updateExistingEntry(entry: EffectiveProfileSwitch): Long {
        changes.add(entry)
        return super.updateExistingEntry(entry)
    }
}