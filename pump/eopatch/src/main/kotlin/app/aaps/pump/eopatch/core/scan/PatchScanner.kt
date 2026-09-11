package app.aaps.pump.eopatch.core.scan

import android.content.Context
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.eopatch.core.ble.IPatchPacketConstant.Companion.SERVICE_UUID
import com.polidea.rxandroidble3.RxBleClient
import com.polidea.rxandroidble3.scan.ScanFilter
import com.polidea.rxandroidble3.scan.ScanSettings
import io.reactivex.rxjava3.core.Single
import java.util.concurrent.TimeUnit

class PatchScanner(context: Context, private val aapsLogger: AAPSLogger) : IPatchScanner {

    private val scanList = ScanList()
    private val rxBleClient: RxBleClient = RxBleClient.create(context)

    private val scanFilter: ScanFilter = ScanFilter.Builder()
        .setServiceUuid(SERVICE_UUID)
        .build()

    private val scanSettings: ScanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
        .build()

    override fun scan(timeout: Long): Single<ScanList> =
        rxBleClient.scanBleDevices(scanSettings, scanFilter)
            .doOnError { e -> aapsLogger.error(LTag.PUMPCOMM, "Scan error: ${e.message}") }
            .take(timeout, TimeUnit.MILLISECONDS)
            .toList()
            .map { list -> scanList.update(list) }
}
