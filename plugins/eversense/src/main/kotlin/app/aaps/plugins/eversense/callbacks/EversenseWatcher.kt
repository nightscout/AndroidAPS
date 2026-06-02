package app.aaps.plugins.eversense.callbacks

import app.aaps.plugins.eversense.enums.EversenseType
import app.aaps.plugins.eversense.models.ActiveAlarm
import app.aaps.plugins.eversense.models.EversenseCGMResult
import app.aaps.plugins.eversense.models.EversenseState

interface EversenseWatcher {
    fun onCGMRead(type: EversenseType, readings: List<EversenseCGMResult>)
    fun onStateChanged(state: EversenseState)
    fun onConnectionChanged(connected: Boolean)
    fun onAlarmReceived(alarm: ActiveAlarm) { /* No-op: optional callback */ }
    fun onTransmitterNotPlaced() { /* No-op: optional callback */ }
    fun onTransmitterReady() { /* No-op: optional callback */ }
}
