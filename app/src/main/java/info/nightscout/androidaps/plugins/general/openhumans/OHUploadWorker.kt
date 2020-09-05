package info.nightscout.androidaps.plugins.general.openhumans

import android.app.Notification
import android.content.Context
import android.net.wifi.WifiManager
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import androidx.work.RxWorker
import androidx.work.WorkerParameters
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.plugins.general.openhumans.OpenHumansUploader.Companion.NOTIFICATION_CHANNEL
import info.nightscout.androidaps.plugins.general.openhumans.OpenHumansUploader.Companion.UPLOAD_NOTIFICATION_ID
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import io.reactivex.Single
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class OHUploadWorker(context: Context, workerParameters: WorkerParameters)
    : RxWorker(context, workerParameters) {

    @Inject
    lateinit var sp: SP

    @Inject
    lateinit var openHumansUploader: OpenHumansUploader

    @Inject
    lateinit var resourceHelper: ResourceHelper

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
            setForegroundAsync(createForegroundInfo())
            openHumansUploader.uploadDataSegmentally()
                .andThen(Single.just(Result.success()))
                .onErrorResumeNext { Single.just(Result.retry()) }
        } else {
            Single.just(Result.retry())
        }
    }

    private fun createForegroundInfo(): ForegroundInfo {
        val title = resourceHelper.gs(info.nightscout.androidaps.R.string.open_humans)

        val notification: Notification = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL)
            .setContentTitle(title)
            .setTicker(title)
            .setContentText(resourceHelper.gs(info.nightscout.androidaps.R.string.your_phone_is_upload_data))
            .setSmallIcon(info.nightscout.androidaps.R.drawable.notif_icon)
            .setOngoing(true)
            .setProgress(0, 0 , true)
            .build()
        return ForegroundInfo(UPLOAD_NOTIFICATION_ID, notification)
    }

}