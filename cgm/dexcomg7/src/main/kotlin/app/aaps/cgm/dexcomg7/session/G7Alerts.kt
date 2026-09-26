package app.aaps.cgm.dexcomg7.session

import app.aaps.cgm.dexcomg7.R
import app.aaps.cgm.dexcomg7.data.G7State
import app.aaps.cgm.dexcomg7.data.G7StateStore
import app.aaps.cgm.dexcomg7.protocol.AlgorithmState
import app.aaps.cgm.dexcomg7.protocol.G7Lifecycle
import app.aaps.cgm.dexcomg7.protocol.G7LifecycleAlert
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.resources.ResourceHelper
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Posts the sensor lifecycle alerts: expiring in 24 h and in 2 h, expired, session ended, sensor failed,
 * connection refused, and warmup finished. Each one once per sensor.
 *
 * Missing readings are not here. AAPS already has an alarm for that which works for every source.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@SingleIn(AppScope::class)
@Inject
class G7Alerts(
    private val store: G7StateStore,
    private val notificationManager: NotificationManager,
    private val rh: ResourceHelper
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private var job: Job? = null

    fun start() {
        job?.cancel()
        job = scope.launch {
            // On every state change (a reading, a refusal) and once a minute for the time-based ones.
            launch { store.state.collect { check(it) } }
            while (isActive) {
                delay(CHECK_INTERVAL_MS)
                check(store.value)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private fun check(state: G7State) {
        if (!state.isPaired) return
        val now = System.currentTimeMillis()
        val activatedAt = state.activatedAt
        val algorithmState = state.latestAlgorithmState?.let { AlgorithmState(it) }

        if (activatedAt != null) {
            when (G7Lifecycle.currentSessionAlert(activatedAt, state.sessionLengthSeconds, now)) {
                G7LifecycleAlert.EXPIRING_SOON       -> once(state, G7LifecycleAlert.EXPIRING_SOON) {
                    notificationManager.post(NotificationId.DEXCOM_G7_SENSOR_EXPIRING, rh.gs(R.string.dexcom_g7_alert_expiring_soon))
                }

                G7LifecycleAlert.EXPIRING_IMMINENTLY -> once(state, G7LifecycleAlert.EXPIRING_IMMINENTLY) {
                    notificationManager.post(NotificationId.DEXCOM_G7_SENSOR_EXPIRING, rh.gs(R.string.dexcom_g7_alert_expiring_imminently))
                }

                G7LifecycleAlert.EXPIRED             -> once(state, G7LifecycleAlert.EXPIRED) {
                    notificationManager.dismiss(NotificationId.DEXCOM_G7_SENSOR_EXPIRING)
                    notificationManager.post(NotificationId.DEXCOM_G7_SENSOR_EXPIRED, rh.gs(R.string.dexcom_g7_alert_expired))
                }

                G7LifecycleAlert.SESSION_ENDED       -> once(state, G7LifecycleAlert.SESSION_ENDED) {
                    notificationManager.dismiss(NotificationId.DEXCOM_G7_SENSOR_EXPIRING)
                    notificationManager.post(NotificationId.DEXCOM_G7_SENSOR_EXPIRED, rh.gs(R.string.dexcom_g7_alert_session_ended))
                }

                else                                 -> Unit
            }
        }

        if (algorithmState?.sensorFailed == true) {
            once(state, G7LifecycleAlert.SENSOR_FAILED) {
                notificationManager.post(NotificationId.DEXCOM_G7_SENSOR_FAILED, rh.gs(R.string.dexcom_g7_alert_sensor_failed))
            }
        }

        // Only for a sensor we saw warming up, not one adopted in the middle of its session.
        if (algorithmState?.hasReliableGlucose == true && activatedAt != null && now - activatedAt < WARMUP_ALERT_WINDOW_MS) {
            once(state, G7LifecycleAlert.WARMUP_FINISHED) {
                notificationManager.post(NotificationId.DEXCOM_G7_WARMUP_FINISHED, rh.gs(R.string.dexcom_g7_alert_warmup_finished), validMinutes = 60)
            }
        }

        // Posted once per run of refusals; cleared by the next reading.
        if (state.lastAuthenticationFailure != null) {
            once(state, G7LifecycleAlert.CONNECTION_REFUSED) {
                notificationManager.post(NotificationId.DEXCOM_G7_CONNECTION_REFUSED, rh.gs(R.string.dexcom_g7_alert_connection_refused))
            }
        } else if (G7LifecycleAlert.CONNECTION_REFUSED.name in state.alertsIssued) {
            // Readings are flowing again.
            notificationManager.dismiss(NotificationId.DEXCOM_G7_CONNECTION_REFUSED)
            store.update { it.copy(alertsIssued = it.alertsIssued - G7LifecycleAlert.CONNECTION_REFUSED.name) }
        }
    }

    private fun once(state: G7State, alert: G7LifecycleAlert, post: () -> Unit) {
        if (alert.name in state.alertsIssued) return
        post()
        store.update { it.copy(alertsIssued = it.alertsIssued + alert.name) }
    }

    companion object {

        const val CHECK_INTERVAL_MS = 60_000L

        /** Warmup is 27 minutes (G7) to about an hour (15-day sensor); anything later is not a fresh sensor. */
        const val WARMUP_ALERT_WINDOW_MS = 6 * 60 * 60_000L
    }
}
