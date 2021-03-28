package info.nightscout.androidaps.database.transactions

import info.nightscout.androidaps.database.entities.UserEntry
import info.nightscout.androidaps.database.entities.UserEntry.*

class UserEntryTransaction(
    val action: Action,
    val s: String,
    val values: MutableList<ValueWithUnit> = mutableListOf<ValueWithUnit>()
) : Transaction<Unit>() {

    override fun run() {
        database.userEntryDao.insert(UserEntry(
            timestamp = System.currentTimeMillis(),
            action = action,
            s = s,
            values = values
        ))
    }
}