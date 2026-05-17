package app.aaps.pump.insight.app_layer.activities

import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.ui.compose.LocalSnackbarHostState
import app.aaps.core.ui.compose.dialogs.GlobalSnackbarHost
import app.aaps.core.utils.HtmlHelper
import app.aaps.pump.insight.InsightAlertService
import app.aaps.pump.insight.compose.InsightAlertScreen
import app.aaps.pump.insight.compose.InsightAlertUiState
import app.aaps.pump.insight.descriptors.Alert
import app.aaps.pump.insight.utils.AlertUtils
import dagger.android.support.DaggerAppCompatActivity
import javax.inject.Inject

class InsightAlertActivity : DaggerAppCompatActivity() {

    @Inject lateinit var alertUtils: AlertUtils
    @Inject lateinit var rxBus: RxBus
    private var alertService: InsightAlertService? = null

    private var state by mutableStateOf(
        InsightAlertUiState(
            icon = Icons.Default.Error,
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
            val snackbarHostState = remember { SnackbarHostState() }
            CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
                Box(modifier = Modifier.fillMaxSize()) {
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
                    GlobalSnackbarHost(
                        rxBus = rxBus,
                        hostState = snackbarHostState,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }
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
        val icon = alert.alertCategory?.let { alertUtils.getAlertIcon(it) } ?: Icons.Default.Error
        val errorCode = alert.alertType?.let { alertUtils.getAlertCode(it) } ?: ""
        val title = alert.alertType?.let { alertUtils.getAlertTitle(it) } ?: ""
        val description = alertUtils.getAlertDescription(alert)?.let { HtmlHelper.fromHtml(it) }
        state = InsightAlertUiState(
            icon = icon,
            errorCode = errorCode,
            title = title,
            description = description,
            alertStatus = alert.alertStatus,
            muteEnabled = true,
            confirmEnabled = true
        )
    }
}
