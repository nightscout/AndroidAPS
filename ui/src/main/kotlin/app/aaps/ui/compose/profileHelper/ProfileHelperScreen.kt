package app.aaps.ui.compose.profileHelper

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.objects.profile.ProfileSealed
import app.aaps.core.ui.compose.AapsTopAppBar
import app.aaps.core.ui.compose.NumberInputRow
import app.aaps.core.ui.compose.clearFocusOnTap
import app.aaps.core.ui.compose.navigation.ElementType
import app.aaps.core.ui.compose.navigation.color
import app.aaps.core.ui.compose.navigation.icon
import app.aaps.core.ui.compose.navigation.labelResId
import app.aaps.ui.R
import app.aaps.ui.compose.profileManagement.ProfileCompareContent
import app.aaps.ui.compose.profileManagement.buildBasalRows
import app.aaps.ui.compose.profileManagement.buildIcRows
import app.aaps.ui.compose.profileManagement.buildIsfRows
import app.aaps.ui.compose.profileManagement.buildTargetRows
import app.aaps.ui.compose.profileManagement.viewmodels.ProfileHelperViewModel
import app.aaps.ui.compose.stats.TddStatsCompose
import java.text.DecimalFormat

/**
 * Enumeration of available profile calculation/source types.
 */
enum class ProfileType {

    MOTOL_DEFAULT,
    DPV_DEFAULT,
    CURRENT,
    AVAILABLE_PROFILE,
    PROFILE_SWITCH
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileHelperScreen(
    viewModel: ProfileHelperViewModel,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    var selectedTab by remember { mutableIntStateOf(0) }
    var showProfileTypeMenu0 by remember { mutableStateOf(false) }
    var showProfileTypeMenu1 by remember { mutableStateOf(false) }

    val profileTypes = remember {
        mutableStateListOf(
            ProfileType.MOTOL_DEFAULT,
            ProfileType.CURRENT
        )
    }
    val ages = remember { mutableStateListOf(15, 15) }
    val weights = remember { mutableStateListOf(0.0, 0.0) }
    val tdds = remember { mutableStateListOf(0.0, 0.0) }
    val pcts = remember { mutableStateListOf(32.0, 32.0) }
    val profileIndices = remember { mutableStateListOf(0, 0) }
    val profileSwitchIndices = remember { mutableStateListOf(0, 0) }

    // Check if comparison data is valid (both profiles have sufficient data)
    val isCompareTabValid = run {
        val profile0Valid = when (profileTypes[0]) {
            ProfileType.MOTOL_DEFAULT, ProfileType.DPV_DEFAULT -> tdds[0] > 0 || weights[0] > 0
            else                                               -> true
        }
        val profile1Valid = when (profileTypes[1]) {
            ProfileType.MOTOL_DEFAULT, ProfileType.DPV_DEFAULT -> tdds[1] > 0 || weights[1] > 0
            else                                               -> true
        }
        profile0Valid && profile1Valid
    }

    // Determine which profile index to use for Clone action
    val cloneIndex = when {
        selectedTab == 2 -> {
            // On comparison tab, prefer profile 0 if it's MOTOL/DPV, otherwise profile 1
            if (profileTypes[0] == ProfileType.MOTOL_DEFAULT || profileTypes[0] == ProfileType.DPV_DEFAULT) 0 else 1
        }

        else             -> selectedTab
    }

    // Determine if Clone button should be shown in top bar (visible on all tabs when comparison is available)
    val showCloneAction = isCompareTabValid && when (selectedTab) {
        2    -> profileTypes[0] == ProfileType.MOTOL_DEFAULT || profileTypes[0] == ProfileType.DPV_DEFAULT ||
            profileTypes[1] == ProfileType.MOTOL_DEFAULT || profileTypes[1] == ProfileType.DPV_DEFAULT

        else -> profileTypes[selectedTab] == ProfileType.MOTOL_DEFAULT || profileTypes[selectedTab] == ProfileType.DPV_DEFAULT
    }

    ProfileHelperContent(
        selectedTab = selectedTab,
        onTabSelected = { selectedTab = it },
        profileTypes = profileTypes,
        showProfileTypeMenu0 = showProfileTypeMenu0,
        onShowProfileTypeMenu0Change = { showProfileTypeMenu0 = it },
        showProfileTypeMenu1 = showProfileTypeMenu1,
        onShowProfileTypeMenu1Change = { showProfileTypeMenu1 = it },
        onProfileTypeChange = { index, type -> profileTypes[index] = type },
        isCompareTabValid = isCompareTabValid,
        showCloneAction = showCloneAction,
        onCloneClick = {
            viewModel.copyToLocal(
                context,
                ages[cloneIndex],
                tdds[cloneIndex],
                weights[cloneIndex],
                pcts[cloneIndex],
                profileTypes[cloneIndex]
            )
        },
        onBackClick = onBackClick,
        focusManager = focusManager,
        comparisonContent = {
            val profile0 = viewModel.getProfile(ages[0], tdds[0], weights[0], pcts[0] / 100.0, profileTypes[0], profileIndices[0], profileSwitchIndices[0])
            val profile1 = viewModel.getProfile(ages[1], tdds[1], weights[1], pcts[1] / 100.0, profileTypes[1], profileIndices[1], profileSwitchIndices[1])

            if (profile0 != null && profile1 != null) {
                val name0 = viewModel.getProfileName(ages[0], tdds[0], weights[0], pcts[0] / 100.0, profileTypes[0], profileIndices[0], profileSwitchIndices[0])
                val name1 = viewModel.getProfileName(ages[1], tdds[1], weights[1], pcts[1] / 100.0, profileTypes[1], profileIndices[1], profileSwitchIndices[1])

                val sealed1 = ProfileSealed.Pure(profile0, null)
                val sealed2 = ProfileSealed.Pure(profile1, null)

                ProfileCompareContent(
                    profile1 = sealed1,
                    profile2 = sealed2,
                    shortHourUnit = viewModel.rh.gs(app.aaps.core.interfaces.R.string.shorthour),
                    icsRows = buildIcRows(sealed1, sealed2, viewModel.dateUtil),
                    icUnits = viewModel.rh.gs(app.aaps.core.ui.R.string.profile_carbs_per_unit),
                    isfsRows = buildIsfRows(sealed1, sealed2, viewModel.profileUtil, viewModel.dateUtil),
                    isfUnits = "${viewModel.getUnits().asText} ${viewModel.rh.gs(app.aaps.core.ui.R.string.profile_per_unit)}",
                    basalsRows = buildBasalRows(sealed1, sealed2, viewModel.dateUtil),
                    basalUnits = viewModel.rh.gs(app.aaps.core.ui.R.string.profile_ins_units_per_hour),
                    targetsRows = buildTargetRows(sealed1, sealed2, viewModel.dateUtil, viewModel.profileUtil),
                    targetUnits = viewModel.getUnits().asText,
                    profileName1 = name0,
                    profileName2 = name1
                )
            } else {
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
                ) {
                    Text(
                        text = stringResource(app.aaps.core.ui.R.string.no_profile_set),
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        },
        profileTabContent = { tabIndex ->
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
            ) {
                when (profileTypes[tabIndex]) {
                    ProfileType.MOTOL_DEFAULT, ProfileType.DPV_DEFAULT -> {
                        DefaultProfileContent(
                            age = ages[tabIndex],
                            onAgeChange = { ages[tabIndex] = it },
                            weight = weights[tabIndex],
                            onWeightChange = { weights[tabIndex] = it },
                            tdd = tdds[tabIndex],
                            onTddChange = { tdds[tabIndex] = it },
                            pct = pcts[tabIndex],
                            onPctChange = { pcts[tabIndex] = it },
                            showPct = profileTypes[tabIndex] == ProfileType.DPV_DEFAULT,
                            showWeight = tdds[tabIndex] == 0.0,
                            showTdd = weights[tabIndex] == 0.0,
                            modifier = Modifier.padding(16.dp)
                        )
                    }

                    ProfileType.CURRENT                                -> {
                        CurrentProfileContent(profileName = state.currentProfileName, modifier = Modifier.padding(16.dp))
                    }

                    ProfileType.AVAILABLE_PROFILE                      -> {
                        AvailableProfileContent(
                            profiles = state.availableProfiles.map { it.toString() },
                            selectedIndex = profileIndices[tabIndex],
                            onProfileSelected = { profileIndices[tabIndex] = it },
                            modifier = Modifier.padding(16.dp)
                        )
                    }

                    ProfileType.PROFILE_SWITCH                         -> {
                        ProfileSwitchContent(
                            profileSwitches = state.profileSwitches.map { it.originalCustomizedName },
                            selectedIndex = profileSwitchIndices[tabIndex],
                            onProfileSwitchSelected = { profileSwitchIndices[tabIndex] = it },
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            if (profileTypes[tabIndex] == ProfileType.MOTOL_DEFAULT || profileTypes[tabIndex] == ProfileType.DPV_DEFAULT) {
                Spacer(modifier = Modifier.height(16.dp))

                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        when {
                            state.isLoadingStats       -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = stringResource(app.aaps.core.ui.R.string.loading),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            state.tddStatsData != null -> {
                                state.tddStatsData?.let { data ->
                                    TddStatsCompose(tddStatsData = data)
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileHelperContent(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    profileTypes: List<ProfileType>,
    showProfileTypeMenu0: Boolean,
    onShowProfileTypeMenu0Change: (Boolean) -> Unit,
    showProfileTypeMenu1: Boolean,
    onShowProfileTypeMenu1Change: (Boolean) -> Unit,
    onProfileTypeChange: (Int, ProfileType) -> Unit,
    isCompareTabValid: Boolean,
    showCloneAction: Boolean,
    onCloneClick: () -> Unit,
    onBackClick: () -> Unit,
    focusManager: FocusManager,
    comparisonContent: @Composable () -> Unit,
    profileTabContent: @Composable (Int) -> Unit,
) {
    Scaffold(
        topBar = {
            AapsTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = ElementType.PROFILE_HELPER.icon(),
                            contentDescription = null,
                            tint = ElementType.PROFILE_HELPER.color(),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.padding(start = 8.dp))
                        Text(stringResource(ElementType.PROFILE_HELPER.labelResId()))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(app.aaps.core.ui.R.string.back))
                    }
                },
                actions = {}
            )
        },
        bottomBar = {
            Button(
                onClick = onCloneClick,
                enabled = showCloneAction,
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (showCloneAction) stringResource(R.string.clone_label)
                    else stringResource(app.aaps.core.ui.R.string.ok)
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .clearFocusOnTap(focusManager)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp)
        ) {
            Surface(tonalElevation = 2.dp) {
                PrimaryTabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { onTabSelected(0) },
                        text = {
                            Box {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(getProfileTypeDisplayName(profileTypes[0]))
                                    IconButton(
                                        onClick = { onShowProfileTypeMenu0Change(true) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.ArrowDropDown,
                                            contentDescription = "Select profile type",
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                DropdownMenu(
                                    expanded = showProfileTypeMenu0,
                                    onDismissRequest = { onShowProfileTypeMenu0Change(false) }
                                ) {
                                    listOf(
                                        ProfileType.MOTOL_DEFAULT to stringResource(R.string.motol_default_profile),
                                        ProfileType.DPV_DEFAULT to stringResource(R.string.dpv_default_profile),
                                        ProfileType.CURRENT to stringResource(R.string.current_profile),
                                        ProfileType.AVAILABLE_PROFILE to stringResource(R.string.available_profile),
                                        ProfileType.PROFILE_SWITCH to stringResource(app.aaps.core.ui.R.string.careportal_profileswitch)
                                    ).forEach { (type, label) ->
                                        DropdownMenuItem(
                                            text = { Text(label) },
                                            onClick = {
                                                onProfileTypeChange(0, type)
                                                onShowProfileTypeMenu0Change(false)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { onTabSelected(1) },
                        text = {
                            Box {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(getProfileTypeDisplayName(profileTypes[1]))
                                    IconButton(
                                        onClick = { onShowProfileTypeMenu1Change(true) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.ArrowDropDown,
                                            contentDescription = "Select profile type",
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                DropdownMenu(
                                    expanded = showProfileTypeMenu1,
                                    onDismissRequest = { onShowProfileTypeMenu1Change(false) }
                                ) {
                                    listOf(
                                        ProfileType.MOTOL_DEFAULT to stringResource(R.string.motol_default_profile),
                                        ProfileType.DPV_DEFAULT to stringResource(R.string.dpv_default_profile),
                                        ProfileType.CURRENT to stringResource(R.string.current_profile),
                                        ProfileType.AVAILABLE_PROFILE to stringResource(R.string.available_profile),
                                        ProfileType.PROFILE_SWITCH to stringResource(app.aaps.core.ui.R.string.careportal_profileswitch)
                                    ).forEach { (type, label) ->
                                        DropdownMenuItem(
                                            text = { Text(label) },
                                            onClick = {
                                                onProfileTypeChange(1, type)
                                                onShowProfileTypeMenu1Change(false)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { onTabSelected(2) },
                        enabled = isCompareTabValid,
                        text = {
                            Text(
                                text = stringResource(R.string.comparation),
                                color = if (!isCompareTabValid) MaterialTheme.colorScheme.error else LocalContentColor.current
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (selectedTab == 2) {
                comparisonContent()
            } else {
                profileTabContent(selectedTab)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun getProfileTypeDisplayName(type: ProfileType): String {
    return when (type) {
        ProfileType.MOTOL_DEFAULT     -> stringResource(R.string.motol_default_profile)
        ProfileType.DPV_DEFAULT       -> stringResource(R.string.dpv_default_profile)
        ProfileType.CURRENT           -> stringResource(R.string.current_profile)
        ProfileType.AVAILABLE_PROFILE -> stringResource(R.string.available_profile)
        ProfileType.PROFILE_SWITCH    -> stringResource(app.aaps.core.ui.R.string.careportal_profileswitch)
    }
}

@Composable
fun DefaultProfileContent(
    age: Int, onAgeChange: (Int) -> Unit,
    weight: Double, onWeightChange: (Double) -> Unit,
    tdd: Double, onTddChange: (Double) -> Unit,
    pct: Double, onPctChange: (Double) -> Unit,
    showPct: Boolean, showWeight: Boolean, showTdd: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(text = stringResource(R.string.profile_parameters), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 16.dp))
        NumberInputRow(
            labelResId = R.string.age,
            value = age.toDouble(),
            onValueChange = { onAgeChange(it.toInt()) },
            valueRange = 1.0..99.0,
            step = 1.0,
            unitLabelResId = app.aaps.core.keys.R.string.units_years
        )
        if (showTdd) NumberInputRow(
            labelResId = app.aaps.core.ui.R.string.tdd_total,
            value = tdd,
            onValueChange = onTddChange,
            valueRange = 0.0..200.0,
            step = 1.0,
            unitLabelResId = app.aaps.core.keys.R.string.units_insulin
        )
        if (showWeight) NumberInputRow(
            labelResId = R.string.weight_label,
            value = weight,
            onValueChange = onWeightChange,
            valueRange = 0.0..150.0,
            step = 1.0,
            unitLabelResId = app.aaps.core.keys.R.string.units_kg
        )
        if (showPct) NumberInputRow(
            labelResId = R.string.basal_pct_from_tdd_label,
            value = pct,
            onValueChange = onPctChange,
            valueRange = 32.0..37.0,
            step = 1.0,
            unitLabelResId = app.aaps.core.keys.R.string.units_percent
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrentProfileContent(profileName: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(text = stringResource(R.string.active_profile), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 12.dp))
        OutlinedTextField(
            value = profileName, onValueChange = {}, readOnly = true, enabled = false,
            label = { Text(stringResource(R.string.current_profile)) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(disabledTextColor = MaterialTheme.colorScheme.onSurface, disabledBorderColor = MaterialTheme.colorScheme.outline, disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvailableProfileContent(profiles: List<String>, selectedIndex: Int, onProfileSelected: (Int) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val selectedProfile = if (profiles.isNotEmpty() && selectedIndex < profiles.size) profiles[selectedIndex] else ""

    Column(modifier = modifier) {
        Text(text = stringResource(R.string.available_profiles), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 12.dp))
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = selectedProfile, onValueChange = {}, readOnly = true,
                label = { Text(stringResource(R.string.selected_profile)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                profiles.forEachIndexed { index, profile ->
                    DropdownMenuItem(text = { Text(profile) }, onClick = { onProfileSelected(index); expanded = false })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSwitchContent(profileSwitches: List<String>, selectedIndex: Int, onProfileSwitchSelected: (Int) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val selectedSwitch = if (profileSwitches.isNotEmpty() && selectedIndex < profileSwitches.size) profileSwitches[selectedIndex] else ""

    Column(modifier = modifier) {
        Text(text = stringResource(R.string.profile_switches), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 12.dp))
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = selectedSwitch, onValueChange = {}, readOnly = true,
                label = { Text(stringResource(app.aaps.core.ui.R.string.careportal_profileswitch)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                profileSwitches.forEachIndexed { index, profileSwitch ->
                    DropdownMenuItem(text = { Text(profileSwitch) }, onClick = { onProfileSwitchSelected(index); expanded = false })
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfileHelperMotolPreview() {
    val focusManager = LocalFocusManager.current
    MaterialTheme {
        ProfileHelperContent(
            selectedTab = 0,
            onTabSelected = {},
            profileTypes = listOf(ProfileType.MOTOL_DEFAULT, ProfileType.CURRENT),
            showProfileTypeMenu0 = false,
            onShowProfileTypeMenu0Change = {},
            showProfileTypeMenu1 = false,
            onShowProfileTypeMenu1Change = {},
            onProfileTypeChange = { _, _ -> },
            isCompareTabValid = false,
            showCloneAction = false,
            onCloneClick = {},
            onBackClick = {},
            focusManager = focusManager,
            comparisonContent = {},
            profileTabContent = {
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
                ) {
                    DefaultProfileContent(
                        age = 15,
                        onAgeChange = {},
                        weight = 0.0,
                        onWeightChange = {},
                        tdd = 25.0,
                        onTddChange = {},
                        pct = 32.0,
                        onPctChange = {},
                        showPct = false,
                        showWeight = false,
                        showTdd = true,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfileHelperCurrentPreview() {
    val focusManager = LocalFocusManager.current
    MaterialTheme {
        ProfileHelperContent(
            selectedTab = 1,
            onTabSelected = {},
            profileTypes = listOf(ProfileType.MOTOL_DEFAULT, ProfileType.CURRENT),
            showProfileTypeMenu0 = false,
            onShowProfileTypeMenu0Change = {},
            showProfileTypeMenu1 = false,
            onShowProfileTypeMenu1Change = {},
            onProfileTypeChange = { _, _ -> },
            isCompareTabValid = true,
            showCloneAction = true,
            onCloneClick = {},
            onBackClick = {},
            focusManager = focusManager,
            comparisonContent = {},
            profileTabContent = {
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
                ) {
                    CurrentProfileContent(
                        profileName = "Profile 1",
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DefaultProfileContentPreview() {
    MaterialTheme {
        DefaultProfileContent(
            age = 15,
            onAgeChange = {},
            weight = 0.0,
            onWeightChange = {},
            tdd = 25.0,
            onTddChange = {},
            pct = 32.0,
            onPctChange = {},
            showPct = true,
            showWeight = false,
            showTdd = true,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CurrentProfileContentPreview() {
    MaterialTheme {
        CurrentProfileContent(
            profileName = "Profile 1",
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AvailableProfileContentPreview() {
    MaterialTheme {
        AvailableProfileContent(
            profiles = listOf("Profile 1", "Profile 2", "Tight control"),
            selectedIndex = 0,
            onProfileSelected = {},
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfileSwitchContentPreview() {
    MaterialTheme {
        ProfileSwitchContent(
            profileSwitches = listOf("Profile 1 (100%)", "Profile 2 (80%)"),
            selectedIndex = 0,
            onProfileSwitchSelected = {},
            modifier = Modifier.padding(16.dp)
        )
    }
}
