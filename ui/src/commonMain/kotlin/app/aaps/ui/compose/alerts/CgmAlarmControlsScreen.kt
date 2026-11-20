package app.aaps.ui.compose.alerts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import app.aaps.core.interfaces.alerts.CgmAlarmType
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.alerts.title
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTopAppBar
import app.aaps.core.ui.compose.stringResource
import kotlinx.coroutines.flow.StateFlow

/**
 * Mute and test the CGM alarms.
 *
 * - Mute: every CGM alarm but urgent low stays quiet until the mute ends. Urgent low is never muted.
 * - Test: each alarm that can have a sound plays once, with its sound and on the same path as a real
 *   alarm, until it is dismissed.
 * - What the platform can and cannot do ([platformNote]), so nothing is silently missing. On iPhone it
 *   says that critical alerts need Apple's entitlement and that there is no minimum volume.
 *
 * Shared by Android and iOS. It only calls back; the alarm state lives in `LocalAlertUtils`.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CgmAlarmControlsScreen(
    mutedUntil: StateFlow<Long>,
    now: () -> Long,
    formatTime: (Long) -> String,
    platformNote: TextRef,
    onMute: (minutes: Int) -> Unit,
    onTest: (CgmAlarmType) -> Unit,
    onBack: () -> Unit,
) {
    val until by mutedUntil.collectAsState()
    Scaffold(
        topBar = {
            AapsTopAppBar(
                title = { Text(stringResource(CoreUiStrings.cgm_alarms_controls_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(CoreUiStrings.back))
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(AapsSpacing.extraLarge),
            verticalArrangement = Arrangement.spacedBy(AapsSpacing.large)
        ) {
            Text(stringResource(CoreUiStrings.cgm_alarms_mute_section), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(CoreUiStrings.cgm_alarms_mute_explanation), style = MaterialTheme.typography.bodyMedium)
            val muted = until > now()
            Text(
                text = if (muted) stringResource(CoreUiStrings.cgm_alarms_muted_until, formatTime(until))
                else stringResource(CoreUiStrings.cgm_alarms_not_muted),
                style = MaterialTheme.typography.bodyLarge,
                color = if (muted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium), verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)) {
                MUTE_MINUTES.forEach { minutes ->
                    OutlinedButton(onClick = { onMute(minutes) }) {
                        Text(
                            if (minutes < 60) stringResource(CoreUiStrings.cgm_alarms_mute_minutes, minutes)
                            else stringResource(CoreUiStrings.cgm_alarms_mute_hours, minutes / 60)
                        )
                    }
                }
                if (muted) TextButton(onClick = { onMute(0) }) { Text(stringResource(CoreUiStrings.cgm_alarms_unmute)) }
            }

            Text(stringResource(CoreUiStrings.cgm_alarms_test_section), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(CoreUiStrings.cgm_alarms_test_explanation), style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium), verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)) {
                TESTABLE.forEach { type ->
                    OutlinedButton(onClick = { onTest(type) }) { Text(stringResource(type.title)) }
                }
            }

            Text(stringResource(platformNote), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Mute choices, in minutes. */
internal val MUTE_MINUTES = listOf(30, 60, 120, 240)

/** The alarms that can have a sound, so a test is worth it. The information messages have none. */
internal val TESTABLE: List<CgmAlarmType> = CgmAlarmType.entries.filter { it.reminds }
