package app.aaps.pump.carelevo.compose.alarm

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.ui.IconsProvider
import app.aaps.core.ui.compose.LocalSnackbarHostState
import app.aaps.pump.carelevo.R
import app.aaps.pump.carelevo.common.CarelevoAlarmNotifier
import app.aaps.pump.carelevo.domain.model.alarm.CarelevoAlarmInfo
import app.aaps.pump.carelevo.domain.type.AlarmCause
import app.aaps.pump.carelevo.domain.type.AlarmType.Companion.isCritical
import app.aaps.pump.carelevo.ext.transformStringResources
import app.aaps.pump.carelevo.presentation.model.AlarmEvent
import app.aaps.pump.carelevo.presentation.viewmodel.CarelevoAlarmViewModel

@Composable
internal fun CarelevoAlarmHost(
    aapsLogger: AAPSLogger,
    carelevoAlarmNotifier: CarelevoAlarmNotifier,
    iconsProvider: IconsProvider
) {
    val context = LocalContext.current
    val snackbarHostState = LocalSnackbarHostState.current
    val viewModel: CarelevoAlarmViewModel = hiltViewModel()
    val notifierAlarms = carelevoAlarmNotifier.alarms.collectAsStateWithLifecycle().value
    val alarmQueue = viewModel.alarmQueue.collectAsStateWithLifecycle().value
    var dismissedAlarmId by remember { mutableStateOf<String?>(null) }
    val currentAlarm = alarmQueue.firstOrNull { it.alarmId != dismissedAlarmId }
    var pendingMessageRes by remember { mutableStateOf<Int?>(null) }
    val pendingMessage = pendingMessageRes?.let { stringResource(it) }

    val requestBtLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.alarmInfo?.let {
                aapsLogger.debug(LTag.PUMPCOMM, "bluetooth enabled for alarm=${it.alarmId}")
            }
        }
    }

    LaunchedEffect(notifierAlarms) {
        if (notifierAlarms.any { it.alarmType.isCritical() || it.cause == AlarmCause.ALARM_ALERT_BLUETOOTH_OFF }) {
            aapsLogger.debug(LTag.PUMPCOMM, "load alarms from notifier alarms=$notifierAlarms")
            viewModel.loadUnacknowledgedAlarms()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.event.collect { event ->
            when (event) {
                AlarmEvent.RequestBluetoothEnable -> {
                    val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    requestBtLauncher.launch(enableBtIntent)
                }

                is AlarmEvent.ShowToastMessage    -> {
                    pendingMessageRes = event.messageRes
                }

                else                              -> Unit
            }
        }
    }

    LaunchedEffect(pendingMessage) {
        pendingMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            pendingMessageRes = null
        }
    }

    LaunchedEffect(currentAlarm?.alarmId) {
        if (currentAlarm != null) {
            viewModel.triggerEvent(AlarmEvent.StartAlarm)
        }
    }

    LaunchedEffect(alarmQueue, dismissedAlarmId) {
        if (dismissedAlarmId != null && alarmQueue.none { it.alarmId == dismissedAlarmId }) {
            dismissedAlarmId = null
        }
    }

    CarelevoAlarmScreen(
        alarm = currentAlarm?.let { buildAlarmUiModel(context, iconsProvider, it) },
        onPrimaryClick = {
            currentAlarm?.let {
                dismissedAlarmId = it.alarmId
                viewModel.triggerEvent(AlarmEvent.ClearAlarm(info = it))
            }
        },
        onMuteClick = {
            viewModel.triggerEvent(AlarmEvent.Mute)
        },
        onMute5MinClick = {
            viewModel.triggerEvent(AlarmEvent.Mute5min)
        }
    )
}

private fun buildAlarmUiModel(
    context: android.content.Context,
    iconsProvider: IconsProvider,
    alarm: CarelevoAlarmInfo
): CarelevoAlarmUiModel {
    val (titleRes, descRes, btnRes) = alarm.cause.transformStringResources()
    val descArgs = buildDescArgsFor(context, alarm)
    val desc = descRes?.let { resId ->
        if (descArgs.isEmpty()) context.getString(resId) else context.getString(resId, *descArgs.toTypedArray())
    } ?: ""

    return CarelevoAlarmUiModel(
        appIcon = iconsProvider.getIcon(),
        title = context.getString(titleRes),
        content = desc,
        primaryButtonText = context.getString(btnRes),
        muteButtonText = context.getString(app.aaps.core.ui.R.string.mute),
        mute5minButtonText = context.getString(app.aaps.core.ui.R.string.mute5min)
    )
}

private fun buildDescArgsFor(context: android.content.Context, alarm: CarelevoAlarmInfo): List<String> = when (alarm.cause) {
    AlarmCause.ALARM_NOTICE_LOW_INSULIN,
    AlarmCause.ALARM_ALERT_OUT_OF_INSULIN -> {
        listOf((alarm.value ?: 0).toString())
    }

    AlarmCause.ALARM_NOTICE_PATCH_EXPIRED -> {
        val totalHours = alarm.value ?: 0
        val days = totalHours / 24
        val hours = totalHours % 24
        listOf(days.toString(), hours.toString())
    }

    AlarmCause.ALARM_NOTICE_BG_CHECK      -> {
        val totalMinutes = alarm.value ?: 0
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        listOf(
            when {
                hours > 0 && minutes > 0 ->
                    context.getString(R.string.common_label_unit_value_duration_hour_and_minute, hours, minutes)

                hours > 0                ->
                    context.getString(R.string.common_label_unit_value_duration_hour, hours)

                else                     ->
                    context.getString(R.string.common_label_unit_value_minute, minutes)
            }
        )
    }

    else                                  -> emptyList()
}
