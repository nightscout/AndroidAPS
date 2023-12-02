package app.aaps.ui.extensions

import app.aaps.database.entities.embedments.InterfaceIDs

fun InterfaceIDs.isPumpHistory() = pumpSerial != null && pumpId != null