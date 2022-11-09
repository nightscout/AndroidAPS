package info.nightscout.androidaps.database.daos.delegated

import info.nightscout.androidaps.database.daos.EffectiveProfileSwitchDao
import info.nightscout.androidaps.database.entities.EffectiveProfileSwitch
import info.nightscout.androidaps.database.interfaces.DBEntry

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