package app.aaps.pump.eopatch.core.scan

import com.polidea.rxandroidble3.scan.ScanResult
import kotlin.math.abs

class ScanList : HashMap<String, Int>() {

    override fun put(key: String, value: Int): Int? {
        val oldRssi = get(key)
        return if (oldRssi == null || oldRssi < value) super.put(key, value) else value
    }

    private fun put(scanResult: ScanResult) {
        val mac = scanResult.bleDevice.macAddress
        val rssi = scanResult.rssi
        val scanRecord = scanResult.scanRecord.bytes
        val oldDevice = scanRecord != null && scanRecord[7].toInt() == 1

        if (oldDevice || rssi < PATCH_MINIMUM_RSSI) return
        put(mac, rssi)
    }

    val nearestDevice: String? get() {
        var nearest: String? = null
        var max = Int.MIN_VALUE

        for ((key, rssi) in entries) {
            if (rssi > max) {
                max = rssi
                nearest = key
            }
        }

        if (size > 1) {
            val sorted = values.sortedDescending()
            val diff = sorted[0] - sorted[1]
            if (abs(diff) < 20) return null
        }

        val nearestValue = nearest?.let { get(it) }
        return if (nearestValue != null && nearestValue >= PATCH_FINAL_RSSI) nearest else null
    }

    @Synchronized fun update(list: List<ScanResult>): ScanList {
        clear()
        list.forEach { put(it) }
        return this
    }

    companion object {
        private const val PATCH_MINIMUM_RSSI = -90
        private const val PATCH_FINAL_RSSI = -70
    }
}
