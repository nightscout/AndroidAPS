package app.aaps.pump.insight

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.MutableLiveData
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.utils.HtmlHelper
import app.aaps.pump.insight.app_layer.activities.InsightAlertActivity
import app.aaps.pump.insight.app_layer.remote_control.ConfirmAlertMessage
import app.aaps.pump.insight.app_layer.remote_control.SnoozeAlertMessage
import app.aaps.pump.insight.app_layer.status.GetActiveAlertMessage
import app.aaps.pump.insight.connection_service.InsightConnectionService
import app.aaps.pump.insight.descriptors.Alert
import app.aaps.pump.insight.descriptors.AlertStatus
import app.aaps.pump.insight.descriptors.AlertType
import app.aaps.pump.insight.descriptors.InsightState
import app.aaps.pump.insight.exceptions.InsightException
import app.aaps.pump.insight.exceptions.app_layer_errors.AppLayerErrorException
import app.aaps.pump.insight.utils.AlertUtils
import app.aaps.pump.insight.utils.ExceptionTranslator
import dagger.android.DaggerService
import javax.inject.Inject

class InsightAlertService : DaggerService(), InsightConnectionService.StateCallback {

    private val localBinder: LocalBinder = LocalBinder()
    private val alertLock = Object()
    val alertLiveData = MutableLiveData<Alert?>()
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var alertUtils: AlertUtils

    private var connectionRequested = false
    private var alert: Alert? = null
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
        synchronized(alertLock) {
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
        vibrator = (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
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
                synchronized(alertLock) {
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
            } catch (_: InterruptedException) {
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
            } catch (_: InterruptedException) {
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
                synchronized(alertLock) {
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
                synchronized(alertLock) {
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

    @SuppressLint("MissingPermission", "UnspecifiedImmutableFlag")
    private fun showNotification(alert: Alert) {
        val notificationBuilder = NotificationCompat.Builder(this, InsightPlugin.ALERT_CHANNEL_ID)
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
        if (description != null) notificationBuilder.setContentText(HtmlHelper.fromHtml(description).toString())
        val fullScreenIntent = Intent(this, InsightAlertActivity::class.java)
        val fullScreenPendingIntent = PendingIntent.getActivity(this, 0, fullScreenIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        notificationBuilder.setFullScreenIntent(fullScreenPendingIntent, true)
        when (alert.alertStatus) {
            AlertStatus.ACTIVE  -> {
                val muteIntent = Intent(this, InsightAlertService::class.java).putExtra("command", "mute")
                val mutePendingIntent = PendingIntent.getService(this, 1, muteIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                notificationBuilder.addAction(0, resourceHelper.gs(app.aaps.core.ui.R.string.mute), mutePendingIntent)
                val confirmIntent = Intent(this, InsightAlertService::class.java).putExtra("command", "confirm")
                val confirmPendingIntent = PendingIntent.getService(this, 2, confirmIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                notificationBuilder.addAction(0, resourceHelper.gs(app.aaps.core.ui.R.string.confirm), confirmPendingIntent)
            }

            AlertStatus.SNOOZED -> {
                val confirmIntent = Intent(this, InsightAlertService::class.java).putExtra("command", "confirm")
                val confirmPendingIntent = PendingIntent.getService(this, 2, confirmIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                notificationBuilder.addAction(0, resourceHelper.gs(app.aaps.core.ui.R.string.confirm), confirmPendingIntent)
            }

            else                -> Unit
        }
        val notification = notificationBuilder.build()
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun dismissNotification() {
        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID)
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    inner class LocalBinder : Binder() {

        val service: InsightAlertService
            get() = this@InsightAlertService
    }

    companion object {

        private const val NOTIFICATION_ID = 31345
    }
}