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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.aaps.core.ui.compose.icons.IcSiteRotation
import app.aaps.core.ui.R as CoreUiR
import app.aaps.ui.R
import app.aaps.ui.compose.siteRotationDialog.viewModels.BodyType
import app.aaps.ui.compose.siteRotationDialog.viewModels.SiteRotationManagementViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteRotationSettingsScreen(
    viewModel: SiteRotationManagementViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.resetToDefaults()
    }

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
                        Spacer(modifier = Modifier.padding(start = 8.dp))
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
                            .padding(horizontal = 16.dp)
                    ) {
                        BodyView(
                            filteredLocationColor = uiState.filteredLocationColor,
                            showPumpSites = uiState.showPumpSites,
                            showCgmSites = uiState.showCgmSites,
                            selectedLocation = uiState.selectedLocation,
                            bodyType = uiState.showBodyType,
                            isFrontView = true,
                            onZoneClick = { location ->
                                viewModel.selectLocation(location)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        BodyView(
                            filteredLocationColor = uiState.filteredLocationColor,
                            showPumpSites = uiState.showPumpSites,
                            showCgmSites = uiState.showCgmSites,
                            selectedLocation = uiState.selectedLocation,
                            bodyType = uiState.showBodyType,
                            isFrontView = false,
                            onZoneClick = { location ->
                                viewModel.selectLocation(location)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Bloc User Profile
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
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
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ProfileRadioButton(
                        text = stringResource(R.string.site_man),
                        selected = uiState.showBodyType == BodyType.MAN,
                        onClick = { viewModel.setBodyType(BodyType.MAN) }
                    )
                    ProfileRadioButton(
                        text = stringResource(R.string.site_woman),
                        selected = uiState.showBodyType == BodyType.WOMAN,
                        onClick = { viewModel.setBodyType(BodyType.WOMAN) }
                    )
                    ProfileRadioButton(
                        text = stringResource(R.string.site_child),
                        selected = uiState.showBodyType == BodyType.CHILD,
                        onClick = { viewModel.setBodyType(BodyType.CHILD) }
                    )
                }
            }

            //Spacer(modifier = Modifier.height(16.dp))

            // Bloc Site Management Profile
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
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
                Spacer(modifier = Modifier.height(8.dp))
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
                        onCheckedChange = { viewModel.setDefaultPumpSites(it) }
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
                        onCheckedChange = { viewModel.setDefaultCgmSites(it) }
                    )
                }
            }
        }
    }
}

@Composable
fun ProfileRadioButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable { onClick() }
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(text = text, modifier = Modifier.padding(start = 4.dp))
    }
}