package info.nightscout.androidaps.database.transactions

import info.nightscout.androidaps.database.entities.UserEntry
import info.nightscout.androidaps.database.entities.UserEntry.Action
import info.nightscout.androidaps.database.entities.UserEntry.Sources
import info.nightscout.androidaps.database.entities.ValueWithUnit

class UserEntryTransaction(
    val action: Action,
    val source: Sources,
    val note: String,
    val values: List<ValueWithUnit?> = listOf()
) : Transaction<Unit>() {

    override fun run() {
        database.userEntryDao.insert(UserEntry(
            timestamp = System.currentTimeMillis(),
            action = action,
            source = source,
            note = note,
            values = values
        ))
    }
}