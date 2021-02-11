package info.nightscout.androidaps.database.transactions

import info.nightscout.androidaps.database.entities.UserEntry

class UserEntryTransaction(
    val action: String,
    val s: String = "",
    val d1: Double = 0.0,
    val d2: Double = 0.0,
    val i1: Int = 0,
    val i2: Int = 0,
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