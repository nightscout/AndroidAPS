package app.aaps.plugins.eversense.callbacks

import app.aaps.plugins.eversense.models.EversenseScanResult

fun interface EversenseScanCallback {
    fun onResult(var0: EversenseScanResult)
}