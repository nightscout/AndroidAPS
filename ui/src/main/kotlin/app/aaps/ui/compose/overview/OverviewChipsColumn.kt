package app.aaps.ui.compose.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.TT
import app.aaps.core.ui.compose.icons.IcSettingsOff
import app.aaps.core.ui.compose.navigation.ElementType
import app.aaps.core.ui.compose.navigation.NavigationRequest
import app.aaps.ui.compose.main.TempTargetChipState
import app.aaps.ui.compose.overview.chips.IobCobChipsRow
import app.aaps.ui.compose.overview.chips.ProfileChip
import app.aaps.ui.compose.overview.chips.RunningModeChip
import app.aaps.ui.compose.overview.chips.TempTargetChip
import app.aaps.ui.compose.overview.graphs.CobUiState
import app.aaps.ui.compose.overview.graphs.IobUiState

@Composable
fun OverviewChipsColumn(
    runningMode: RM.Mode,
    runningModeText: String,
    runningModeProgress: Float,
    isSimpleMode: Boolean,
    profileName: String,
    isProfileModified: Boolean,
    profileProgress: Float,
    tempTargetText: String,
    tempTargetState: TempTargetChipState,
    tempTargetProgress: Float,
    tempTargetReason: TT.Reason?,
    iobUiState: IobUiState,
    cobUiState: CobUiState,
    onNavigate: (NavigationRequest) -> Unit,
    modifier: Modifier = Modifier,
    trailingContent: @Composable (RowScope.() -> Unit)? = null
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        if (trailingContent != null) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val chipsWidth = (maxWidth * 0.4f).coerceIn(140.dp, 220.dp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.width(chipsWidth),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        NarrowChips(
                            runningMode = runningMode,
                            runningModeText = runningModeText,
                            runningModeProgress = runningModeProgress,
                            isSimpleMode = isSimpleMode,
                            profileName = profileName,
                            isProfileModified = isProfileModified,
                            profileProgress = profileProgress,
                            tempTargetText = tempTargetText,
                            tempTargetState = tempTargetState,
                            tempTargetProgress = tempTargetProgress,
                            tempTargetReason = tempTargetReason,
                            onNavigate = onNavigate
                        )
                    }
                    Row(
                        modifier = Modifier.weight(1f),
                        content = trailingContent
                    )
                }
            }
        } else {
            NarrowChips(
                runningMode = runningMode,
                runningModeText = runningModeText,
                runningModeProgress = runningModeProgress,
                isSimpleMode = isSimpleMode,
                profileName = profileName,
                isProfileModified = isProfileModified,
                profileProgress = profileProgress,
                tempTargetText = tempTargetText,
                tempTargetState = tempTargetState,
                tempTargetProgress = tempTargetProgress,
                tempTargetReason = tempTargetReason,
                onNavigate = onNavigate
            )
        }
        IobCobChipsRow(
            iobUiState = iobUiState,
            cobUiState = cobUiState
        )
    }
}

@Composable
private fun NarrowChips(
    runningMode: RM.Mode,
    runningModeText: String,
    runningModeProgress: Float,
    isSimpleMode: Boolean,
    profileName: String,
    isProfileModified: Boolean,
    profileProgress: Float,
    tempTargetText: String,
    tempTargetState: TempTargetChipState,
    tempTargetProgress: Float,
    tempTargetReason: TT.Reason?,
    onNavigate: (NavigationRequest) -> Unit
) {
    if (runningModeText.isNotEmpty()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RunningModeChip(
                mode = runningMode,
                text = runningModeText,
                progress = runningModeProgress,
                modifier = Modifier.weight(1f),
                onClick = { onNavigate(NavigationRequest.Element(ElementType.RUNNING_MODE)) }
            )
            if (isSimpleMode) {
                Icon(
                    imageVector = IcSettingsOff,
                    contentDescription = stringResource(app.aaps.core.ui.R.string.simple_mode),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(20.dp)
                )
            }
        }
    }
    if (profileName.isNotEmpty()) {
        ProfileChip(
            profileName = profileName,
            isModified = isProfileModified,
            progress = profileProgress,
            onClick = { onNavigate(NavigationRequest.Element(ElementType.PROFILE_MANAGEMENT)) }
        )
    }
    if (tempTargetText.isNotEmpty()) {
        TempTargetChip(
            targetText = tempTargetText,
            state = tempTargetState,
            progress = tempTargetProgress,
            reason = tempTargetReason,
            onClick = { onNavigate(NavigationRequest.Element(ElementType.TEMP_TARGET_MANAGEMENT)) }
        )
    }
}

