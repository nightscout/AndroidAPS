package app.aaps.pump.insight.app_layer.activities

import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.aaps.core.utils.HtmlHelper
import app.aaps.pump.insight.InsightAlertService
import app.aaps.pump.insight.R
import app.aaps.pump.insight.compose.InsightAlertScreen
import app.aaps.pump.insight.compose.InsightAlertUiState
import app.aaps.pump.insight.descriptors.Alert
import app.aaps.pump.insight.utils.AlertUtils
import dagger.android.support.DaggerAppCompatActivity
import javax.inject.Inject

class InsightAlertActivity : DaggerAppCompatActivity() {

    @Inject lateinit var alertUtils: AlertUtils
    private var alertService: InsightAlertService? = null

    private var state by mutableStateOf(
        InsightAlertUiState(
            iconRes = R.drawable.ic_error,
            errorCode = "",
            title = "",
            description = null,
            alertStatus = null,
            muteEnabled = true,
            confirmEnabled = true
        )
    )

    private val serviceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            alertService = (binder as InsightAlertService.LocalBinder).service.also {
                it.alertLiveData.observe(this@InsightAlertActivity) { alert: Alert? ->
                    if (alert == null) finish() else update(alert)
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            alertService = null
        }
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            InsightAlertScreen(
                state = state,
                onMute = {
                    state = state.copy(muteEnabled = false)
                    alertService?.mute()
                },
                onConfirm = {
                    state = state.copy(muteEnabled = false, confirmEnabled = false)
                    alertService?.confirm()
                }
            )
        }
        bindService(Intent(this, InsightAlertService::class.java), serviceConnection, BIND_AUTO_CREATE)
        setFinishOnTouchOutside(false)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        val keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        keyguardManager.requestDismissKeyguard(this, null)
    }

    override fun onDestroy() {
        unbindService(serviceConnection)
        super.onDestroy()
    }

    private fun update(alert: Alert) {
        val iconRes = alert.alertCategory?.let { alertUtils.getAlertIcon(it) } ?: R.drawable.ic_error
        val errorCode = alert.alertType?.let { alertUtils.getAlertCode(it) } ?: ""
        val title = alert.alertType?.let { alertUtils.getAlertTitle(it) } ?: ""
        val description = alertUtils.getAlertDescription(alert)?.let { HtmlHelper.fromHtml(it) }
        state = InsightAlertUiState(
            iconRes = iconRes,
            errorCode = errorCode,
            title = title,
            description = description,
            alertStatus = alert.alertStatus,
            muteEnabled = true,
            confirmEnabled = true
        )
    }
}
