package info.nightscout.androidaps.plugins.general.openhumans

import android.content.Context
import android.net.wifi.WifiManager
import androidx.work.RxWorker
import androidx.work.WorkerParameters
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.utils.sharedPreferences.SP
import io.reactivex.Single
import javax.inject.Inject

class OHUploadWorker(context: Context, workerParameters: WorkerParameters)
    : RxWorker(context, workerParameters) {

    @Inject
    lateinit var sp: SP

    @Inject
    lateinit var openHumansUploader: OpenHumansUploader

    override fun createWork(): Single<Result> = Single.defer {

        // Here we inject every time we create work
        // We could build our own WorkerFactory with dagger but this will create conflicts with other Workers
        // (see https://medium.com/wonderquill/how-to-pass-custom-parameters-to-rxworker-worker-using-dagger-2-f4cfbc9892ba)
        // This class will be replaced with new DB

        (applicationContext as MainApp).androidInjector().inject(this)

        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val wifiOnly = sp.getBoolean("key_oh_wifi_only", true)
        val isConnectedToWifi = wifiManager?.isWifiEnabled ?: false && wifiManager?.connectionInfo?.networkId != -1
        if (!wifiOnly || (wifiOnly && isConnectedToWifi)) {
            openHumansUploader.uploadData()
                .andThen(Single.just(Result.success()))
                .onErrorResumeNext { Single.just(Result.retry()) }
        } else {
            Single.just(Result.retry())
        }
    }

}