package info.nightscout.androidaps.plugins.general.openhumans

import android.content.Context
import android.net.wifi.WifiManager
import androidx.work.RxWorker
import androidx.work.WorkerParameters
import info.nightscout.androidaps.utils.SP
import io.reactivex.Single

class OHUploadWorker(
    val context: Context,
    workerParameters: WorkerParameters
) : RxWorker(context, workerParameters) {

    override fun createWork() = Single.defer {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val wifiOnly = SP.getBoolean("key_oh_wifi_only", true)
        val isConnectedToWifi = wifiManager?.isWifiEnabled ?: false && wifiManager?.connectionInfo?.networkId != -1
        if (!wifiOnly || (wifiOnly && isConnectedToWifi)) {
            OpenHumansUploader.uploadData()
                .andThen(Single.just(Result.success()))
                .onErrorResumeNext { Single.just(Result.retry()) }
        } else {
            Single.just(Result.retry())
        }
    }

}