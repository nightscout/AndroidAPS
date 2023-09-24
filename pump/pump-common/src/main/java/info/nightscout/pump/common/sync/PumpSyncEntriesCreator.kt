package info.nightscout.pump.common.sync

import app.aaps.interfaces.pump.defs.PumpType

interface PumpSyncEntriesCreator {

    fun generateTempId(objectA: Any): Long
    fun model(): PumpType
    fun serialNumber(): String
}