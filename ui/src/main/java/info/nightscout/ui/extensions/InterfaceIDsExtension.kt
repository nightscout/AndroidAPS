package info.nightscout.ui.extensions

import info.nightscout.database.entities.embedments.InterfaceIDs

fun InterfaceIDs.isPumpHistory() = pumpSerial != null && pumpId != null