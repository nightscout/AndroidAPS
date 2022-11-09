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
}