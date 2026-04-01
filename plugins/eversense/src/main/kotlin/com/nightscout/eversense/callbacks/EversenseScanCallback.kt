package com.nightscout.eversense.callbacks

import com.nightscout.eversense.models.EversenseScanResult

interface EversenseScanCallback {
    fun onResult(var0: EversenseScanResult)
}