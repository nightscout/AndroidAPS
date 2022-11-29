package info.nightscout.interfaces.nsclient

import info.nightscout.database.transactions.TransactionGlucoseValue

interface StoreDataForDb {
    val glucoseValues: MutableList<TransactionGlucoseValue>
}