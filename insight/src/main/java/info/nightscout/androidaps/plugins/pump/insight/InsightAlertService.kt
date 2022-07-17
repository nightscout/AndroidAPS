package info.nightscout.androidaps.plugins.pump.insight

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.MutableLiveData
import dagger.android.DaggerService
import info.nightscout.androidaps.insight.R
import info.nightscout.androidaps.plugins.pump.insight.activities.InsightAlertActivity
import info.nightscout.androidaps.plugins.pump.insight.app_layer.remote_control.ConfirmAlertMessage
import info.nightscout.androidaps.plugins.pump.insight.app_layer.remote_control.SnoozeAlertMessage
import info.nightscout.androidaps.plugins.pump.insight.app_layer.status.GetActiveAlertMessage
import info.nightscout.androidaps.plugins.pump.insight.connection_service.InsightConnectionService
import info.nightscout.androidaps.plugins.pump.insight.descriptors.Alert
import info.nightscout.androidaps.plugins.pump.insight.descriptors.AlertStatus
import info.nightscout.androidaps.plugins.pump.insight.descriptors.AlertType
import info.nightscout.androidaps.plugins.pump.insight.descriptors.InsightState
import info.nightscout.androidaps.plugins.pump.insight.exceptions.InsightException
import info.nightscout.androidaps.plugins.pump.insight.exceptions.app_layer_errors.AppLayerErrorException
import info.nightscout.androidaps.plugins.pump.insight.utils.AlertUtils
import info.nightscout.androidaps.plugins.pump.insight.utils.ExceptionTranslator
import info.nightscout.androidaps.utils.HtmlHelper.fromHtml
import info.nightscout.androidaps.interfaces.ResourceHelper
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import javax.inject.Inject

class InsightAlertService : DaggerService(), InsightConnectionService.StateCallback {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var alertUtils: AlertUtils

    private val localBinder: LocalBinder = LocalBinder()
    private var connectionRequested = false
    private val `$alertLock`: Any = arrayOfNulls<Any>(0)
    private var alert: Alert? = null
    val alertLiveData = MutableLiveData<Alert?>()
    private var thread: Thread? = null
    private var vibrator: Vibrator? = null
    private var vibrating = false
    private var connectionService: InsightConnectionService? = null
    private var ignoreTimestamp: Long = 0
    private var ignoreType: AlertType? = null
    private val serviceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            connectionService = (binder as InsightConnectionService.LocalBinder).service
            connectionService?.let {
                it.registerStateCallback(this@InsightAlertService)
                onStateChanged(it.state)
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            connectionService = null
        }
    }

    fun ignore(alertType: AlertType?) {
        synchronized(`$alertLock`) {
            if (alertType == null) {
                ignoreTimestamp = 0
                ignoreType = null
            } else {
                ignoreTimestamp = System.currentTimeMillis()
                ignoreType = alertType
            }
        }
    }

    override fun onBind(intent: Intent): IBinder {
        return localBinder
    }

    @SuppressWarnings("deprecation", "RedundantSuppression")
    override fun onCreate() {
        super.onCreate()
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
        bindService(Intent(this, InsightConnectionService::class.java), serviceConnection, BIND_AUTO_CREATE)
        alertLiveData.value = null
    }

    override fun onDestroy() {
        if (thread != null) thread?.interrupt()
        unbindService(serviceConnection)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        //noinspection StatementWithEmptyBody
        if (intent == null) {
            // service is being restarted
        } else if ("mute" == intent.getStringExtra("command")) {
            mute()
        } else if ("confirm" == intent.getStringExtra("command")) {
            dismissNotification()
            confirm()
        }
        return START_STICKY
    }

    override fun onStateChanged(state: InsightState?) {
        if (state == InsightState.CONNECTED) {
            thread = Thread { queryActiveAlert() }
            thread?.start()
        } else {
            dismissNotification()
            if (thread != null) thread?.interrupt()
        }
    }

    private fun queryActiveAlert() {
        while (!Thread.currentThread().isInterrupted) {
            try {
                synchronized(`$alertLock`) {
                    val alert = connectionService!!.requestMessage(GetActiveAlertMessage()).await().alert
                    if (alert == null || alert.alertType == ignoreType && System.currentTimeMillis() - ignoreTimestamp < 10000) {
                        if (connectionRequested) {
                            connectionService!!.withdrawConnectionRequest(this)
                            connectionRequested = false
                        }
                        alertLiveData.postValue(null)
                        this.alert = null
                        dismissNotification()
                        stopAlerting()
                    } else if (alert != this.alert) {
                        if (!connectionRequested) {
                            connectionService!!.requestConnection(this)
                            connectionRequested = true
                        }
                        showNotification(alert)
                        alertLiveData.postValue(alert)
                        this.alert = alert
                        if (alert.alertStatus == AlertStatus.SNOOZED) stopAlerting() else alert()
                    }
                }
            } catch (ignored: InterruptedException) {
                connectionService?.withdrawConnectionRequest(thread as Any)
                break
            } catch (e: AppLayerErrorException) {
                aapsLogger.info(LTag.PUMP, "Exception while fetching alert: " + e.javaClass.canonicalName + " (" + e.errorCode + ")")
            } catch (e: InsightException) {
                aapsLogger.info(LTag.PUMP, "Exception while fetching alert: " + e.javaClass.simpleName)
            } catch (e: Exception) {
                aapsLogger.error(LTag.PUMP, "Exception while fetching alert", e)
            }
            try {
                Thread.sleep(1000)
            } catch (e: InterruptedException) {
                break
            }
        }
        if (connectionRequested) {
            connectionService?.withdrawConnectionRequest(thread as Any)
            connectionRequested = false
        }
        stopAlerting()
        alertLiveData.postValue(null)
        alert = null
        dismissNotification()
        thread = null
    }

    private fun alert() {
        if (!vibrating) {
            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 1000, 1000, 0), 0))
            vibrating = true
        }
    }

    private fun stopAlerting() {
        if (vibrating) {
            vibrator?.cancel()
            vibrating = false
        }
    }

    fun mute() {
        Thread(Runnable {
            try {
                synchronized(`$alertLock`) {
                    if (alert == null) return@Runnable
                    alert?.let {
                        it.alertStatus = AlertStatus.SNOOZED
                        alertLiveData.postValue(it)
                        stopAlerting()
                        showNotification(it)
                        val snoozeAlertMessage = SnoozeAlertMessage()
                        snoozeAlertMessage.alertID = it.alertId
                        connectionService?.run { requestMessage(snoozeAlertMessage).await() }
                    }
                }
            } catch (e: AppLayerErrorException) {
                aapsLogger.info(LTag.PUMP, "Exception while muting alert: " + e.javaClass.canonicalName + " (" + e.errorCode + ")")
                ExceptionTranslator.makeToast(this@InsightAlertService, e)
            } catch (e: InsightException) {
                aapsLogger.info(LTag.PUMP, "Exception while muting alert: " + e.javaClass.simpleName)
                ExceptionTranslator.makeToast(this@InsightAlertService, e)
            } catch (e: Exception) {
                aapsLogger.error(LTag.PUMP, "Exception while muting alert", e)
                ExceptionTranslator.makeToast(this@InsightAlertService, e)
            }
        }).start()
    }

    fun confirm() {
        Thread(Runnable {
            try {
                synchronized(`$alertLock`) {
                    if (alert == null) return@Runnable
                    stopAlerting()
                    alertLiveData.postValue(null)
                    dismissNotification()
                    val confirmAlertMessage = ConfirmAlertMessage()
                    alert?.let { confirmAlertMessage.alertID = it.alertId }
                    connectionService?.run { requestMessage(confirmAlertMessage).await() }
                    alert = null
                }
            } catch (e: AppLayerErrorException) {
                aapsLogger.info(LTag.PUMP, "Exception while confirming alert: " + e.javaClass.canonicalName + " (" + e.errorCode + ")")
                ExceptionTranslator.makeToast(this@InsightAlertService, e)
            } catch (e: InsightException) {
                aapsLogger.info(LTag.PUMP, "Exception while confirming alert: " + e.javaClass.simpleName)
                ExceptionTranslator.makeToast(this@InsightAlertService, e)
            } catch (e: Exception) {
                aapsLogger.error(LTag.PUMP, "Exception while confirming alert", e)
                ExceptionTranslator.makeToast(this@InsightAlertService, e)
            }
        }).start()
    }

    private fun showNotification(alert: Alert) {
        val notificationBuilder = NotificationCompat.Builder(this, LocalInsightPlugin.ALERT_CHANNEL_ID)
        notificationBuilder.priority = NotificationCompat.PRIORITY_MAX
        notificationBuilder.setCategory(NotificationCompat.CATEGORY_ALARM)
        notificationBuilder.setVibrate(LongArray(0))
        notificationBuilder.setShowWhen(false)
        notificationBuilder.setOngoing(true)
        notificationBuilder.setOnlyAlertOnce(true)
        notificationBuilder.setAutoCancel(false)
        alert.alertCategory?.let { notificationBuilder.setSmallIcon(alertUtils.getAlertIcon(it)) }
        alert.alertType?.let { notificationBuilder.setContentTitle(alertUtils.getAlertCode(it) + " – " + alertUtils.getAlertTitle(it)) }
        val description = alertUtils.getAlertDescription(alert)
        if (description != null) notificationBuilder.setContentText(fromHtml(description).toString())
        val fullScreenIntent = Intent(this, InsightAlertActivity::class.java)
        val fullScreenPendingIntent = PendingIntent.getActivity(this, 0, fullScreenIntent, PendingIntent.FLAG_UPDATE_CURRENT)
        notificationBuilder.setFullScreenIntent(fullScreenPendingIntent, true)
        when (alert.alertStatus) {
            AlertStatus.ACTIVE  -> {
                    val muteIntent = Intent(this, InsightAlertService::class.java).putExtra("command", "mute")
                    val mutePendingIntent = PendingIntent.getService(this, 1, muteIntent, PendingIntent.FLAG_UPDATE_CURRENT)
                    notificationBuilder.addAction(0, resourceHelper.gs(R.string.mute_alert), mutePendingIntent)
                    val confirmIntent = Intent(this, InsightAlertService::class.java).putExtra("command", "confirm")
                    val confirmPendingIntent = PendingIntent.getService(this, 2, confirmIntent, PendingIntent.FLAG_UPDATE_CURRENT)
                    notificationBuilder.addAction(0, resourceHelper.gs(R.string.confirm), confirmPendingIntent)
                }
            AlertStatus.SNOOZED -> {
                val confirmIntent = Intent(this, InsightAlertService::class.java).putExtra("command", "confirm")
                val confirmPendingIntent = PendingIntent.getService(this, 2, confirmIntent, PendingIntent.FLAG_UPDATE_CURRENT)
                notificationBuilder.addAction(0, resourceHelper.gs(R.string.confirm), confirmPendingIntent)
            }
            else                -> Unit
        }
        val notification = notificationBuilder.build()
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun dismissNotification() {
        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID)
        stopForeground(true)
    }

    inner class LocalBinder : Binder() {

        val service: InsightAlertService
            get() = this@InsightAlertService
    }

    companion object {

        private const val NOTIFICATION_ID = 31345
    }
}