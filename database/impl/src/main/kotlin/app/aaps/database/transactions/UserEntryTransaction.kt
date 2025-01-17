package app.aaps.database.transactions

import app.aaps.database.entities.UserEntry

class UserEntryTransaction(private val entries: List<UserEntry>) : Transaction<List<UserEntry>>() {

    override fun run(): List<UserEntry> {

        for (entry in entries)
            database.userEntryDao.insert(entry)
        return entries
    }
}