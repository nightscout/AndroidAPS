package info.nightscout.database.entities.interfaces

import info.nightscout.database.entities.embedments.InterfaceIDs

interface TraceableDBEntry: DBEntry {
    var version: Int
    var dateCreated: Long
    var isValid: Boolean
    var referenceId: Long?
    @Suppress("PropertyName") var interfaceIDs_backing: InterfaceIDs?

    val historic: Boolean get() = referenceId != null

    val foreignKeysValid: Boolean get() = referenceId != 0L

    var interfaceIDs: InterfaceIDs
        get() {
            var value = this.interfaceIDs_backing
            if (value == null) {
                value = InterfaceIDs()
                interfaceIDs_backing = value
            }
            return value
        }
        set(value) {
            interfaceIDs_backing = value
        }

    fun interfaceIdsEqualsTo(other: TraceableDBEntry): Boolean =
        interfaceIDs.nightscoutId == other.interfaceIDs.nightscoutId &&
            interfaceIDs.nightscoutSystemId == other.interfaceIDs.nightscoutSystemId &&
            interfaceIDs.pumpType == other.interfaceIDs.pumpType &&
            interfaceIDs.pumpSerial == other.interfaceIDs.pumpSerial &&
            interfaceIDs.temporaryId == other.interfaceIDs.temporaryId &&
            interfaceIDs.pumpId == other.interfaceIDs.pumpId &&
            interfaceIDs.startId == other.interfaceIDs.startId &&
            interfaceIDs.endId == other.interfaceIDs.endId

}