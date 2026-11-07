package app.aaps.ui.compose.main

import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalClientRole
import app.aaps.core.ui.compose.icons.IcAutomation
import app.aaps.core.ui.compose.icons.Pump
import app.aaps.core.ui.compose.navigation.NavigationRequest
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.UiStrings

@Composable
fun MainNavigationBar(
    onManageClick: () -> Unit,
    onTreatmentClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** On a client, hide the mutating nav buttons (Treatments + Scenes) until paired. Always true on master. */
    masterOrPairedClient: Boolean = true,
    quickWizardCount: Int = 0,
    onAutomationClick: () -> Unit = {},
    /** Total scenes + automation items defined — drives whether the nav button is shown at all. */
    automationTotal: Int = 0,
    /** Subset of [automationTotal] that the user can activate right now — drives the badge. */
    automationCount: Int = 0,
    pumpSetupPlugin: PluginBase? = null,
    bgSetupPlugin: PluginBase? = null,
    bgQualityBadgeIcon: ImageVector? = null,
    bgQualityBadgeTint: Color = Color.Unspecified,
    bgQualityBadgeDescription: String? = null,
    objectivesSetupPlugin: PluginBase? = null,
    objectivesProgressText: String? = null,
    /**
     * What [objectivesProgressText] means, in words. The badge glyph "9/10" cannot be spoken (see
     * [badgeState]), so the progress has to arrive here already worded by the caller that owns the
     * two counts.
     */
    objectivesProgressDescription: String? = null,
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

    // A client whose role cannot use what a button opens does not get the button: the master would refuse it.
    val role = LocalClientRole.current
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        windowInsets = WindowInsets(0),
        modifier = modifier
    ) {
        // Treatment action button (opens bottom sheet) — hidden on an unpaired client (its bolus/carbs
        // ride the Client-Control channel and would be dropped); always shown on a master.
        if (masterOrPairedClient && role.allows(ElementType.TREATMENT.role)) {
            NavigationBarItem(
                selected = false,
                modifier = Modifier.badgeState(
                    if (quickWizardCount > 0) stringResource(UiStrings.a11y_badge_available, quickWizardCount) else null
                ),
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
                            contentDescription = stringResource(CoreUiStrings.treatments),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                label = { Text(text = stringResource(CoreUiStrings.treatments), textAlign = TextAlign.Center) },
                colors = navColors
            )
        }

        // Scenes/automation button — visible whenever scenes or automation items exist on a paired
        // client (regardless of pump/loop/profile state). The badge counts only items the user can act
        // on right now; gated items are visible inside the sheet, dimmed with reason.
        if (masterOrPairedClient && role.allows(ElementType.SCENE.role) && automationTotal > 0) {
            NavigationBarItem(
                selected = false,
                modifier = Modifier.badgeState(
                    if (automationCount > 0) stringResource(UiStrings.a11y_badge_available, automationCount) else null
                ),
                onClick = onAutomationClick,
                icon = {
                    BadgedBox(
                        badge = {
                            if (automationCount > 0) {
                                Badge(containerColor = AapsTheme.generalColors.statusNormal, contentColor = Color.Black) {
                                    Text(text = automationCount.toString())
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = IcAutomation,
                            contentDescription = stringResource(CoreUiStrings.scenes),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                label = { Text(text = stringResource(CoreUiStrings.scenes), textAlign = TextAlign.Center) },
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
                    contentDescription = stringResource(CoreUiStrings.manage)
                )
            },
            label = { Text(text = stringResource(CoreUiStrings.manage), textAlign = TextAlign.Center) },
            colors = navColors
        )

        // Pump setup (visible only when pump not initialized and has compose content)
        if (pumpSetupPlugin != null) {
            val label = pumpSetupPlugin.pluginDescription.pluginName?.let { stringResource(it) } ?: pumpSetupPlugin.name
            NavigationBarItem(
                selected = false,
                modifier = Modifier.badgeState(stringResource(UiStrings.a11y_badge_attention)),
                onClick = { onNavigate(NavigationRequest.Plugin(pumpSetupPlugin::class.simpleName.orEmpty())) },
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
                label = { Text(text = label, textAlign = TextAlign.Center) },
                colors = navColors
            )
        }

        // BG source shortcut (visible when BG quality check reports FLAT or DOUBLED)
        val bgIcon = bgSetupPlugin?.pluginDescription?.icon
        if (bgSetupPlugin != null && bgIcon != null) {
            val label = bgSetupPlugin.pluginDescription.pluginName?.let { stringResource(it) } ?: bgSetupPlugin.name
            NavigationBarItem(
                selected = false,
                // The quality problem, or a bare "action needed" when the badge is only an exclamation
                // mark. bgQualityBadgeDescription used to sit on the Icon below, inside the slot
                // Material3 clears, so it was never spoken.
                modifier = Modifier.badgeState(bgQualityBadgeDescription ?: stringResource(UiStrings.a11y_badge_attention)),
                onClick = { onNavigate(NavigationRequest.Plugin(bgSetupPlugin::class.simpleName.orEmpty())) },
                icon = {
                    BadgedBox(
                        badge = {
                            if (bgQualityBadgeIcon != null) {
                                Badge(containerColor = Color.Transparent) {
                                    Icon(
                                        imageVector = bgQualityBadgeIcon,
                                        // Decorative here: its meaning is on the item as a state above.
                                        contentDescription = null,
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
                label = { Text(text = label, textAlign = TextAlign.Center) },
                colors = navColors
            )
        }

        // Objectives progress (visible while any objective is not yet accomplished)
        val objectivesIcon = objectivesSetupPlugin?.pluginDescription?.icon
        if (objectivesSetupPlugin != null && objectivesIcon != null) {
            val label = objectivesSetupPlugin.pluginDescription.pluginName?.let { stringResource(it) } ?: objectivesSetupPlugin.name
            NavigationBarItem(
                selected = false,
                // "9 of 10 done", so a blind user hears why this button is on her screen at all.
                modifier = Modifier.badgeState(objectivesProgressDescription ?: stringResource(UiStrings.a11y_badge_attention)),
                onClick = { onNavigate(NavigationRequest.Plugin(objectivesSetupPlugin::class.simpleName.orEmpty())) },
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
                label = { Text(text = label, textAlign = TextAlign.Center) },
                colors = navColors
            )
        }

        // Loop accept action (visible only when AAPS has a pending suggestion in open loop)
        if (loopActionAvailable) {
            NavigationBarItem(
                selected = false,
                // The badge is a constant "1": one suggestion is waiting. Spoken so it matches.
                modifier = Modifier.badgeState(stringResource(UiStrings.a11y_badge_available, 1)),
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
                            contentDescription = stringResource(UiStrings.loop_accept_nav_label),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                label = { Text(text = stringResource(UiStrings.loop_accept_nav_label), textAlign = TextAlign.Center) },
                colors = navColors
            )
        }

        // Permissions (visible only when some are missing)
        if (permissionsMissing) {
            NavigationBarItem(
                selected = false,
                // The badge is a bare dot, so there is nothing to read even in the unmerged tree.
                modifier = Modifier.badgeState(stringResource(UiStrings.a11y_badge_attention)),
                onClick = onPermissionsClick,
                icon = {
                    BadgedBox(badge = { Badge() }) {
                        Icon(
                            imageVector = Icons.Default.GppMaybe,
                            contentDescription = stringResource(UiStrings.permission_sheet_title),
                        )
                    }
                },
                label = { Text(text = stringResource(UiStrings.permission_nav_label), textAlign = TextAlign.Center) },
                colors = navColors.copy(
                    unselectedIconColor = MaterialTheme.colorScheme.error,
                    unselectedTextColor = MaterialTheme.colorScheme.error,
                )
            )
        }
    }
}

/**
 * Speaks what a navigation button's badge shows, or changes nothing when there is no badge.
 *
 * None of the badges in this bar can be read by a screen reader. Material3's `NavigationBarItem`
 * wraps its whole icon slot in `clearAndSetSemantics {}` whenever a label is shown, and every item
 * here has a label, so the badge - the count, the progress, the dot - is dropped from the semantics
 * tree before it ever reaches the user. A blind user heard "Objectives" with no idea why it was
 * there, and the quality badge beside the glucose source was silent in the same way even though
 * someone had already given it a description.
 *
 * [stateDescription] rather than a content description, because this modifier lands on the
 * selectable node outside the cleared slot: a content description there is announced *before* the
 * item's own name instead of replacing it, which stutters the name, and clearing semantics would
 * take the click with it. Every item in this bar is `selected = false`, so nothing is given up by
 * setting a state - on a genuinely selectable item this would need to keep the framework's
 * "Selected" as well.
 */
private fun Modifier.badgeState(state: String?): Modifier =
    if (state != null) semantics { stateDescription = state } else this
