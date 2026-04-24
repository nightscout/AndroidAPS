package app.aaps.ui.compose.main

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.GppMaybe
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.PlayCircle
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
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.icons.IcAutomation
import app.aaps.core.ui.compose.icons.Pump
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
    pumpSetupPlugin: PluginBase? = null,
    bgSetupPlugin: PluginBase? = null,
    bgQualityBadgeIcon: ImageVector? = null,
    bgQualityBadgeTint: Color = Color.Unspecified,
    bgQualityBadgeDescription: String? = null,
    objectivesSetupPlugin: PluginBase? = null,
    objectivesProgressText: String? = null,
    onNavigate: (NavigationRequest) -> Unit = {},
    permissionsMissing: Boolean = false,
    onPermissionsClick: () -> Unit = {},
    loopActionAvailable: Boolean = false,
    onLoopActionClick: () -> Unit = {}
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
                            contentDescription = stringResource(CoreUiR.string.scenes),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                label = { Text(text = stringResource(CoreUiR.string.scenes)) },
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
        if (pumpSetupPlugin != null) {
            val label = stringResource(pumpSetupPlugin.pluginDescription.pluginName)
            NavigationBarItem(
                selected = false,
                onClick = { onNavigate(NavigationRequest.Plugin(pumpSetupPlugin.javaClass.simpleName)) },
                icon = {
                    BadgedBox(
                        badge = { Badge { Text("!") } }
                    ) {
                        Icon(
                            imageVector = pumpSetupPlugin.pluginDescription.icon ?: Pump,
                            contentDescription = label,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                label = { Text(text = label) },
                colors = navColors
            )
        }

        // BG source shortcut (visible when BG quality check reports FLAT or DOUBLED)
        val bgIcon = bgSetupPlugin?.pluginDescription?.icon
        if (bgSetupPlugin != null && bgIcon != null) {
            val label = stringResource(bgSetupPlugin.pluginDescription.pluginName)
            NavigationBarItem(
                selected = false,
                onClick = { onNavigate(NavigationRequest.Plugin(bgSetupPlugin.javaClass.simpleName)) },
                icon = {
                    BadgedBox(
                        badge = {
                            if (bgQualityBadgeIcon != null) {
                                Badge(containerColor = Color.Transparent) {
                                    Icon(
                                        imageVector = bgQualityBadgeIcon,
                                        contentDescription = bgQualityBadgeDescription,
                                        tint = bgQualityBadgeTint,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else {
                                Badge { Text("!") }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = bgIcon,
                            contentDescription = label,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                label = { Text(text = label) },
                colors = navColors
            )
        }

        // Objectives progress (visible while any objective is not yet accomplished)
        val objectivesIcon = objectivesSetupPlugin?.pluginDescription?.icon
        if (objectivesSetupPlugin != null && objectivesIcon != null) {
            val label = stringResource(objectivesSetupPlugin.pluginDescription.pluginName)
            NavigationBarItem(
                selected = false,
                onClick = { onNavigate(NavigationRequest.Plugin(objectivesSetupPlugin.javaClass.simpleName)) },
                icon = {
                    BadgedBox(
                        badge = {
                            if (objectivesProgressText != null) {
                                Badge(
                                    containerColor = AapsTheme.generalColors.statusWarning,
                                    contentColor = Color.Black
                                ) { Text(text = objectivesProgressText) }
                            } else {
                                Badge(
                                    containerColor = AapsTheme.generalColors.statusWarning,
                                    contentColor = Color.Black
                                ) { Text("!") }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = objectivesIcon,
                            contentDescription = label,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                label = { Text(text = label) },
                colors = navColors
            )
        }

        // Loop accept action (visible only when AAPS has a pending suggestion in open loop)
        if (loopActionAvailable) {
            NavigationBarItem(
                selected = false,
                onClick = onLoopActionClick,
                icon = {
                    BadgedBox(
                        badge = {
                            Badge(containerColor = AapsTheme.generalColors.statusNormal, contentColor = Color.Black) {
                                Text(text = "1")
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayCircle,
                            contentDescription = stringResource(R.string.loop_accept_nav_label),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                label = { Text(text = stringResource(R.string.loop_accept_nav_label)) },
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
