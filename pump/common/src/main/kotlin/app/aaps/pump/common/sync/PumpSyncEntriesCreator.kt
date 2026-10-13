package app.aaps.pump.common.sync

import app.aaps.core.data.pump.defs.PumpType

interface PumpSyncEntriesCreator {

    fun generateTempId(objectA: Any): Long
    fun model(): PumpType
    fun serialNumber(): String
}