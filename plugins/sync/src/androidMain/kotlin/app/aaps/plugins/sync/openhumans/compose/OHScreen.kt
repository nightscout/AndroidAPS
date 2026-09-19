package app.aaps.plugins.sync.openhumans.compose

import app.aaps.core.ui.compose.stringResource
import app.aaps.core.ui.CoreUiStrings
import app.aaps.plugins.sync.SyncStrings
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.ToolbarConfig

@Composable
internal fun OHScreen(
    viewModel: OHViewModel,
    setToolbarConfig: (ToolbarConfig) -> Unit,
    onNavigateBack: () -> Unit,
    onSetup: () -> Unit,
    onLogout: () -> Unit,
    onUploadNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val title = stringResource(SyncStrings.open_humans)

    LaunchedEffect(Unit) {
        setToolbarConfig(
            ToolbarConfig(
                title = title,
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(CoreUiStrings.back))
                    }
                },
                actions = {}
            )
        )
    }

    OHScreenContent(
        uiState = uiState,
        onSetup = onSetup,
        onLogout = onLogout,
        onUploadNow = onUploadNow,
        modifier = modifier
    )
}

/**
 * @see OHScreenNotLoggedInPreview
 * @see OHScreenLoggedInPreview
 */
@Composable
internal fun OHScreenContent(
    uiState: OHUiState,
    onSetup: () -> Unit,
    onLogout: () -> Unit,
    onUploadNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(AapsSpacing.extraLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = rememberVectorPainter(OHLogo),
            contentDescription = null,
            modifier = Modifier
                .size(200.dp)
                .padding(bottom = AapsSpacing.extraLarge)
        )

        Text(
            text = stringResource(SyncStrings.open_humans),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )

        Text(
            text = if (uiState.isLoggedIn)
                stringResource(SyncStrings.setup_completed_info)
            else
                stringResource(SyncStrings.not_setup_info),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = AapsSpacing.medium)
        )

        if (uiState.isLoggedIn && uiState.projectMemberId != null) {
            Text(
                text = stringResource(SyncStrings.project_member_id, uiState.projectMemberId),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = AapsSpacing.extraLarge)
            )
        }

        if (!uiState.isLoggedIn) {
            Button(
                onClick = onSetup,
                modifier = Modifier.padding(top = AapsSpacing.extraLarge)
            ) {
                Text(stringResource(SyncStrings.setup))
            }
        }

        if (uiState.isLoggedIn) {
            Button(
                onClick = onUploadNow,
                modifier = Modifier.padding(top = AapsSpacing.extraLarge)
            ) {
                Text(stringResource(SyncStrings.upload_now))
            }

            Button(
                onClick = onLogout,
                modifier = Modifier.padding(top = AapsSpacing.extraLarge)
            ) {
                Text(stringResource(SyncStrings.logout))
            }
        }
    }
}
