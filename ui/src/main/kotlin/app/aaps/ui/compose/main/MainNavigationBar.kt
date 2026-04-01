package app.aaps.ui.compose.main

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.GppMaybe
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.icons.IcAutomation
import app.aaps.core.ui.compose.navigation.NavigationRequest
import app.aaps.ui.R
import app.aaps.core.ui.R as CoreUiR

@Composable
fun MainNavigationBar(
    onManageClick: () -> Unit,
    onTreatmentClick: () -> Unit,
    modifier: Modifier = Modifier,
    quickWizardCount: Int = 0,
    onAutomationClick: () -> Unit = {},
    automationCount: Int = 0,
    pumpSetupClassName: String? = null,
    pumpSetupIcon: ImageVector? = null,
    pumpSetupLabel: String? = null,
    onNavigate: (NavigationRequest) -> Unit = {},
    permissionsMissing: Boolean = false,
    onPermissionsClick: () -> Unit = {}
) {
    val navColors = NavigationBarItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
        selectedTextColor = MaterialTheme.colorScheme.onSurface,
        indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    )

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
    ) {
        // Treatment action button (opens bottom sheet)
        NavigationBarItem(
            selected = false,
            onClick = onTreatmentClick,
            icon = {
                BadgedBox(
                    badge = {
                        if (quickWizardCount > 0) {
                            Badge(containerColor = AapsTheme.generalColors.statusNormal, contentColor = Color.Black) {
                                Text(text = quickWizardCount.toString())
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Fastfood,
                        contentDescription = stringResource(CoreUiR.string.treatments),
                        modifier = Modifier.size(24.dp)
                    )
                }
            },
            label = { Text(text = stringResource(CoreUiR.string.treatments)) },
            colors = navColors
        )

        // Automation action button (visible only when actions are available)
        if (automationCount > 0) {
            NavigationBarItem(
                selected = false,
                onClick = onAutomationClick,
                icon = {
                    BadgedBox(
                        badge = {
                            Badge(containerColor = AapsTheme.generalColors.statusNormal, contentColor = Color.Black) {
                                Text(text = automationCount.toString())
                            }
                        }
                    ) {
                        Icon(
                            imageVector = IcAutomation,
                            contentDescription = stringResource(CoreUiR.string.automation),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                label = { Text(text = stringResource(CoreUiR.string.automation)) },
                colors = navColors
            )
        }

        // Manage action button (opens bottom sheet)
        NavigationBarItem(
            selected = false,
            onClick = onManageClick,
            icon = {
                Icon(
                    imageVector = Icons.Default.ManageAccounts,
                    contentDescription = stringResource(CoreUiR.string.manage)
                )
            },
            label = { Text(text = stringResource(CoreUiR.string.manage)) },
            colors = navColors
        )

        // Pump setup (visible only when pump not initialized and has compose content)
        if (pumpSetupClassName != null && pumpSetupIcon != null && pumpSetupLabel != null) {
            NavigationBarItem(
                selected = false,
                onClick = { onNavigate(NavigationRequest.Plugin(pumpSetupClassName)) },
                icon = {
                    BadgedBox(
                        badge = { Badge { Text("!") } }
                    ) {
                        Icon(
                            imageVector = pumpSetupIcon,
                            contentDescription = pumpSetupLabel,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                label = { Text(text = pumpSetupLabel) },
                colors = navColors
            )
        }

        // Permissions (visible only when some are missing)
        if (permissionsMissing) {
            NavigationBarItem(
                selected = false,
                onClick = onPermissionsClick,
                icon = {
                    BadgedBox(badge = { Badge() }) {
                        Icon(
                            imageVector = Icons.Default.GppMaybe,
                            contentDescription = stringResource(R.string.permission_sheet_title),
                        )
                    }
                },
                label = { Text(text = stringResource(R.string.permission_nav_label)) },
                colors = navColors.copy(
                    unselectedIconColor = MaterialTheme.colorScheme.error,
                    unselectedTextColor = MaterialTheme.colorScheme.error,
                )
            )
        }
    }
}
