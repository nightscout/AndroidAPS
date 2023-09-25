package info.nightscout.database.impl.daos.delegated

import app.aaps.database.entities.EffectiveProfileSwitch
import app.aaps.database.entities.interfaces.DBEntry
import info.nightscout.database.impl.daos.EffectiveProfileSwitchDao

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