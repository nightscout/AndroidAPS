package app.aaps.plugins.sync.nsclientV3.data

import app.aaps.core.interfaces.aps.APSResult
import app.aaps.core.interfaces.nsclient.ProcessedDeviceStatusData
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class ProcessedDeviceStatusDataImpl @Inject constructor(
    private val apsResultProvider: () -> APSResult
) : ProcessedDeviceStatusData {

    override var pumpData: ProcessedDeviceStatusData.PumpData? = null

    override var device: ProcessedDeviceStatusData.Device? = null

    override val uploaderMap = HashMap<String, ProcessedDeviceStatusData.Uploader>()

    override var openAPSData = ProcessedDeviceStatusData.OpenAPSData()

    override val openApsTimestamp: Long
        get() = if (openAPSData.clockSuggested != 0L) openAPSData.clockSuggested else -1

    override fun getAPSResult(): APSResult? =
        openAPSData.suggested?.let { apsResultProvider().with(it) }

    override val uploaderStatus: String
        get() {
            var minBattery = 100
            for ((_, uploader) in uploaderMap) {
                if (minBattery > uploader.battery) minBattery = uploader.battery
            }
            return "$minBattery%"
        }
}
