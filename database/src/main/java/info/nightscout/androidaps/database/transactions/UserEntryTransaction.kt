package info.nightscout.androidaps.database.transactions

import info.nightscout.androidaps.database.entities.UserEntry
import info.nightscout.androidaps.database.entities.UserEntry.*

class UserEntryTransaction(
    val action: Action,
    val s: String = "",
    val d1: ValueWithUnit = ValueWithUnit(0.0, Units.None),
    val d2: ValueWithUnit = ValueWithUnit(0.0, Units.None),
    val i1: ValueWithUnit = ValueWithUnit(0, Units.None),
    val i2: ValueWithUnit = ValueWithUnit(0, Units.None)
) : Transaction<Unit>() {

    override fun run() {

        database.userEntryDao.insert(UserEntry(
            timestamp = System.currentTimeMillis(),
            action = action,
            s = s,
            d1 = d1,
            d2 = d2,
            i1 = i1,
            i2 = i2
        ))
    }
}