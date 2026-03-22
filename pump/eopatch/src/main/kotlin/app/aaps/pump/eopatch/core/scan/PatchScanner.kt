package app.aaps.pump.eopatch.core.scan

import android.content.Context
import android.util.Log
import app.aaps.pump.eopatch.core.ble.IPatchPacketConstant.Companion.SERVICE_UUID
import com.polidea.rxandroidble3.RxBleClient
import com.polidea.rxandroidble3.scan.ScanFilter
import com.polidea.rxandroidble3.scan.ScanSettings
import io.reactivex.rxjava3.core.Single
import java.util.concurrent.TimeUnit

class PatchScanner(context: Context) : IPatchScanner {

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
            .doOnError { e -> Log.e("EOPATCH_CORE", "Error: ${e.message}") }
            .take(timeout, TimeUnit.MILLISECONDS)
            .toList()
            .map { list -> scanList.update(list) }
}
