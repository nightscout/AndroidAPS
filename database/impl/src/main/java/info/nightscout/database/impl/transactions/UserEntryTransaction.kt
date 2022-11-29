package info.nightscout.database.impl.transactions

import info.nightscout.database.entities.UserEntry

class UserEntryTransaction(private val entries: List<UserEntry>) : Transaction<List<UserEntry>>() {

    override fun run(): List<UserEntry> {

        for (entry in entries)
            database.userEntryDao.insert(entry)
        return entries
    }
}