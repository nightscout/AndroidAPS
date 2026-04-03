package app.aaps.ui.compose.siteRotationDialog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.aaps.core.data.model.TE
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.icons.IcSiteRotation
import app.aaps.core.ui.compose.siteRotation.BodyType
import app.aaps.core.ui.compose.siteRotation.BodyView
import app.aaps.ui.R
import app.aaps.ui.compose.siteRotationDialog.viewModels.SiteRotationManagementViewModel
import app.aaps.ui.compose.siteRotationDialog.viewModels.SiteRotationUiState
import app.aaps.core.ui.R as CoreUiR

@Composable
fun SiteRotationSettingsScreen(
    viewModel: SiteRotationManagementViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    SiteRotationSettingsContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onZoneClick = { viewModel.selectLocation(it) },
        onBodyTypeChange = { viewModel.setBodyType(it) },
        onDefaultPumpSitesChange = { viewModel.setDefaultPumpSites(it) },
        onDefaultCgmSitesChange = { viewModel.setDefaultCgmSites(it) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SiteRotationSettingsContent(
    uiState: SiteRotationUiState,
    onNavigateBack: () -> Unit,
    onZoneClick: (TE.Location) -> Unit,
    onBodyTypeChange: (BodyType) -> Unit,
    onDefaultPumpSitesChange: (Boolean) -> Unit,
    onDefaultCgmSitesChange: (Boolean) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = IcSiteRotation,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(AapsSpacing.medium))
                        Text(stringResource(CoreUiR.string.settings))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(CoreUiR.string.back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AapsSpacing.extraLarge)
                    ) {
                        BodyView(
                            filteredLocationColor = uiState.filteredLocationColor,
                            showPumpSites = uiState.showPumpSites,
                            showCgmSites = uiState.showCgmSites,
                            selectedLocation = uiState.selectedLocation,
                            bodyType = uiState.showBodyType,
                            isFrontView = true,
                            onZoneClick = onZoneClick,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(AapsSpacing.medium))
                        BodyView(
                            filteredLocationColor = uiState.filteredLocationColor,
                            showPumpSites = uiState.showPumpSites,
                            showCgmSites = uiState.showCgmSites,
                            selectedLocation = uiState.selectedLocation,
                            bodyType = uiState.showBodyType,
                            isFrontView = false,
                            onZoneClick = onZoneClick,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AapsSpacing.extraLarge)
            ) {
                Text(
                    text = stringResource(R.string.user_profile),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stringResource(R.string.user_profile_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(AapsSpacing.medium))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ProfileRadioButton(
                        text = stringResource(R.string.site_man),
                        selected = uiState.showBodyType == BodyType.MAN,
                        onClick = { onBodyTypeChange(BodyType.MAN) }
                    )
                    ProfileRadioButton(
                        text = stringResource(R.string.site_woman),
                        selected = uiState.showBodyType == BodyType.WOMAN,
                        onClick = { onBodyTypeChange(BodyType.WOMAN) }
                    )
                    ProfileRadioButton(
                        text = stringResource(R.string.site_child),
                        selected = uiState.showBodyType == BodyType.CHILD,
                        onClick = { onBodyTypeChange(BodyType.CHILD) }
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AapsSpacing.extraLarge)
            ) {
                Text(
                    text = stringResource(R.string.site_management),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stringResource(R.string.site_management_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(AapsSpacing.medium))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(CoreUiR.string.careportal_pump_site_management),
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = uiState.showPumpSites,
                        onCheckedChange = onDefaultPumpSitesChange
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(CoreUiR.string.careportal_cgm_site_management),
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = uiState.showCgmSites,
                        onCheckedChange = onDefaultCgmSitesChange
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileRadioButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable { onClick() }
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(text = text, modifier = Modifier.padding(start = AapsSpacing.small))
    }
}

@Preview(showBackground = true)
@Composable
private fun SiteRotationSettingsPreview() {
    MaterialTheme {
        SiteRotationSettingsContent(
            uiState = SiteRotationUiState(
                showBodyType = BodyType.MAN,
                showPumpSites = true,
                showCgmSites = true
            ),
            onNavigateBack = {},
            onZoneClick = {},
            onBodyTypeChange = {},
            onDefaultPumpSitesChange = {},
            onDefaultCgmSitesChange = {}
        )
    }
}
