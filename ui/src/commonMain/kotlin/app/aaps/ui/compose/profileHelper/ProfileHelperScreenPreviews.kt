package app.aaps.ui.compose.profileHelper

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Preview(showBackground = true)
@Composable
internal fun ProfileHelperMotolPreview() {
    val focusManager = LocalFocusManager.current
    MaterialTheme {
        ProfileHelperContent(
            selectedTab = 0,
            onTabSelected = {},
            profileTypes = listOf(ProfileType.MOTOL_DEFAULT, ProfileType.CURRENT),
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
internal fun ProfileHelperCurrentPreview() {
    val focusManager = LocalFocusManager.current
    MaterialTheme {
        ProfileHelperContent(
            selectedTab = 1,
            onTabSelected = {},
            profileTypes = listOf(ProfileType.MOTOL_DEFAULT, ProfileType.CURRENT),
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
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Active Profile", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
                        Text("Profile 1", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
internal fun DefaultProfileContentPreview() {
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
internal fun AvailableProfileContentPreview() {
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
internal fun ProfileSwitchContentPreview() {
    MaterialTheme {
        ProfileSwitchContent(
            profileSwitches = listOf("Profile 1 (100%)", "Profile 2 (80%)"),
            selectedIndex = 0,
            onProfileSwitchSelected = {},
            modifier = Modifier.padding(16.dp)
        )
    }
}
