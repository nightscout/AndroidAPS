package com.nightscout.eversense.callbacks

import com.nightscout.eversense.enums.EversenseType
import com.nightscout.eversense.models.EversenseCGMResult
import com.nightscout.eversense.models.EversenseState

interface EversenseWatcher {
    fun onCGMRead(type: EversenseType, readings: List<EversenseCGMResult>)
    fun onStateChanged(state: EversenseState)
    fun onConnectionChanged(connected: Boolean)
}