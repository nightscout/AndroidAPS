package app.aaps.core.data.db

import app.aaps.core.data.pump.defs.PumpType

data class IDs(
    var nightscoutSystemId: String? = null,
    var nightscoutId: String? = null,
    var pumpType: PumpType? = null, // if == USER pumpSerial & pumpId can be null
    var pumpSerial: String? = null,
    var temporaryId: Long? = null, // temporary id for pump synchronization, when pump id is not available
    var pumpId: Long? = null,
    var startId: Long? = null,
    var endId: Long? = null
)