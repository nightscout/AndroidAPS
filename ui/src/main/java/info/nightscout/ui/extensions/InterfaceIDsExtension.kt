package info.nightscout.ui.extensions

import app.aaps.database.entities.embedments.InterfaceIDs

fun InterfaceIDs.isPumpHistory() = pumpSerial != null && pumpId != null