package app.aaps.ui.compose.manageSheet

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.pump.actions.CustomAction
import app.aaps.core.keys.interfaces.ClientRole
import app.aaps.core.keys.interfaces.ElementVisibility
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.LocalClientRole
import app.aaps.core.ui.compose.LocalConfig
import app.aaps.core.ui.compose.MasterOfflineBanner
import app.aaps.core.ui.compose.consumeOverscroll
import app.aaps.core.ui.compose.icons.IcCancelExtendedBolus
import app.aaps.core.ui.compose.icons.IcTbrCancel
import app.aaps.core.ui.compose.masterEditingEnabled
import app.aaps.core.ui.compose.navigation.NavigationRequest
import app.aaps.core.ui.compose.navigation.color
import app.aaps.core.ui.compose.navigation.description
import app.aaps.core.ui.compose.navigation.icon
import app.aaps.core.ui.compose.navigation.label
import app.aaps.core.ui.compose.rememberBringIntoViewOnExpand
import app.aaps.core.ui.compose.stringResource
import app.aaps.core.ui.compose.stringResourceOrNull

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageBottomSheet(
    onDismiss: () -> Unit,
    isSimpleMode: Boolean,
    // Visibility flags
    showTempTarget: Boolean,
    showTempBasal: Boolean,
    showCancelTempBasal: Boolean,
    showExtendedBolus: Boolean,
    showCancelExtendedBolus: Boolean,
    showBatteryChange: Boolean,
    showFill: Boolean,
    showAuthorizedClients: Boolean,
    showPairWithMaster: Boolean,
    showMutatingActions: Boolean,
    showPump: Boolean,
    // Cancel text strings
    cancelTempBasalText: String,
    cancelExtendedBolusText: String,
    // Pump
    isPatchPump: Boolean,
    pumpPlugin: PluginBase,
    customActions: List<CustomAction>,
    // Callbacks
    onNavigate: (NavigationRequest) -> Unit,
    onCancelTempBasalClick: () -> Unit,
    onCancelExtendedBolusClick: () -> Unit,
    onCustomActionClick: (CustomAction) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        ManageBottomSheetContent(
            isSimpleMode = isSimpleMode,
            showTempTarget = showTempTarget,
            showTempBasal = showTempBasal,
            showCancelTempBasal = showCancelTempBasal,
            showExtendedBolus = showExtendedBolus,
            showCancelExtendedBolus = showCancelExtendedBolus,
            showBatteryChange = showBatteryChange,
            showFill = showFill,
            showAuthorizedClients = showAuthorizedClients,
            showPairWithMaster = showPairWithMaster,
            showMutatingActions = showMutatingActions,
            showPump = showPump,
            cancelTempBasalText = cancelTempBasalText,
            cancelExtendedBolusText = cancelExtendedBolusText,
            isPatchPump = isPatchPump,
            pumpPlugin = pumpPlugin,
            customActions = customActions,
            onDismiss = onDismiss,
            onNavigate = onNavigate,
            onCancelTempBasalClick = onCancelTempBasalClick,
            onCancelExtendedBolusClick = onCancelExtendedBolusClick,
            onCustomActionClick = onCustomActionClick
        )
    }
}

/**
 * @see ManageBottomSheetContentPreview
 */
@Composable
internal fun ManageBottomSheetContent(
    isSimpleMode: Boolean = false,
    showTempTarget: Boolean,
    showTempBasal: Boolean,
    showCancelTempBasal: Boolean,
    showExtendedBolus: Boolean,
    showCancelExtendedBolus: Boolean,
    showBatteryChange: Boolean = false,
    showFill: Boolean = false,
    showAuthorizedClients: Boolean = false,
    showPairWithMaster: Boolean = false,
    showMutatingActions: Boolean = true,
    showPump: Boolean = true,
    cancelTempBasalText: String,
    cancelExtendedBolusText: String,
    isPatchPump: Boolean = false,
    pumpPlugin: PluginBase? = null,
    customActions: List<CustomAction>,
    onDismiss: () -> Unit = {},
    onNavigate: (NavigationRequest) -> Unit = {},
    onCancelTempBasalClick: () -> Unit = {},
    onCancelExtendedBolusClick: () -> Unit = {},
    onCustomActionClick: (CustomAction) -> Unit = {}
) {
    // True on a master / reachable client; false on a client whose master is unreachable or has control disabled.
    // Drives the banner AND the per-button disabled state (relayed buttons grey out, only Pair-with-master stays live).
    val editingEnabled = masterEditingEnabled()
    Column(
        modifier = Modifier
            .consumeOverscroll()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        // Explains offline vs. control-disabled; renders nothing when editing is enabled.
        MasterOfflineBanner(editingEnabled = editingEnabled)
        // A paired read-only client gets almost nothing here; say why. Not an error, so not the offline banner.
        // (The role first: it is Admin in previews, so LocalConfig, which previews do not provide, is never read there.)
        if (LocalClientRole.current == ClientRole.ReadOnly && editingEnabled && showMutatingActions && LocalConfig.current.AAPSCLIENT)
            ReadOnlyRoleBanner()
        // Section: Manage
        SectionHeader(stringResource(CoreUiStrings.manage))

        GridSection(modifier = Modifier.padding(horizontal = 16.dp)) {
            // Profiles are the exception: an unpaired client still needs to SEE them (it receives them
            // from Nightscout and calculates with them), so the entry stays and the screen opens
            // read-only there. Every other editor here can only write, so it is hidden instead.
            add(ElementType.PROFILE_MANAGEMENT) { modifier ->
                ManageGridItem(
                    elementType = ElementType.PROFILE_MANAGEMENT,
                    onDismiss = onDismiss,
                    onNavigate = onNavigate,
                    modifier = modifier
                )
            }
            // Mutating editors ride the signed Client-Control channel — hidden on an unpaired client
            // (showMutatingActions=false), always shown on a master. SITE_ROTATION now rides it too (its record +
            // edit write path is Client-Control-migrated), so it is gated alongside the others.
            if (showMutatingActions) {
                add(ElementType.INSULIN_MANAGEMENT) { modifier ->
                    ManageGridItem(
                        elementType = ElementType.INSULIN_MANAGEMENT,
                        onDismiss = onDismiss,
                        onNavigate = onNavigate,
                        modifier = modifier
                    )
                }
                if (showTempTarget) {
                    add(ElementType.TEMP_TARGET_MANAGEMENT) { modifier ->
                        ManageGridItem(
                            elementType = ElementType.TEMP_TARGET_MANAGEMENT,
                            onDismiss = onDismiss,
                            onNavigate = onNavigate,
                            modifier = modifier
                        )
                    }
                }
                add(ElementType.QUICK_WIZARD_MANAGEMENT) { modifier ->
                    ManageGridItem(
                        elementType = ElementType.QUICK_WIZARD_MANAGEMENT,
                        onDismiss = onDismiss,
                        onNavigate = onNavigate,
                        modifier = modifier
                    )
                }
                add(ElementType.SCENE_MANAGEMENT) { modifier ->
                    ManageGridItem(
                        elementType = ElementType.SCENE_MANAGEMENT,
                        onDismiss = onDismiss,
                        onNavigate = onNavigate,
                        modifier = modifier
                    )
                }
                add(ElementType.AUTOMATION_MANAGEMENT) { modifier ->
                    ManageGridItem(
                        elementType = ElementType.AUTOMATION_MANAGEMENT,
                        onDismiss = onDismiss,
                        onNavigate = onNavigate,
                        modifier = modifier
                    )
                }
                add(ElementType.FOOD_MANAGEMENT) { modifier ->
                    ManageGridItem(
                        elementType = ElementType.FOOD_MANAGEMENT,
                        onDismiss = onDismiss,
                        onNavigate = onNavigate,
                        modifier = modifier
                    )
                }
                add(ElementType.SITE_ROTATION) { modifier ->
                    ManageGridItem(
                        elementType = ElementType.SITE_ROTATION,
                        onDismiss = onDismiss,
                        onNavigate = onNavigate,
                        modifier = modifier
                    )
                }
            }
            if (pumpPlugin != null && showPump) {
                add(ElementType.PUMP) { modifier ->
                    ManageGridItem(
                        text = stringResource(CoreUiStrings.pump_management),
                        icon = pumpPlugin.pluginDescription.icon ?: ElementType.PUMP.icon(),
                        color = ElementType.PUMP.color(),
                        onDismiss = onDismiss,
                        onClick = { onNavigate(NavigationRequest.Element(ElementType.PUMP)) },
                        description = pumpPlugin.name,
                        modifier = modifier
                    )
                }
            }
            if (showAuthorizedClients) {
                add(ElementType.AUTHORIZED_CLIENTS) { modifier ->
                    ManageGridItem(
                        elementType = ElementType.AUTHORIZED_CLIENTS,
                        onDismiss = onDismiss,
                        onNavigate = onNavigate,
                        modifier = modifier
                    )
                }
            }
            if (showPairWithMaster) {
                add(ElementType.PAIR_WITH_MASTER) { modifier ->
                    ManageGridItem(
                        elementType = ElementType.PAIR_WITH_MASTER,
                        onDismiss = onDismiss,
                        onNavigate = onNavigate,
                        modifier = modifier
                    )
                }
            }
        }

        // Section: Device maintenance & basal — every item (sensor insert, fill, battery change, TBR/EB) now rides
        // Client-Control, so the whole section is gated MASTER_OR_PAIRED_CLIENT (hidden on an unpaired client).
        if (showMutatingActions && (showTempBasal || showCancelTempBasal || showExtendedBolus || showCancelExtendedBolus)) {
            GridSection(
                modifier = Modifier.padding(horizontal = 16.dp),
                // Drawn only when this phone's role leaves an item in the section.
                header = {
                    HorizontalDivider(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                }
            ) {
                add(ElementType.SENSOR_INSERT) { modifier ->
                    ManageGridItem(
                        elementType = ElementType.SENSOR_INSERT,
                        onDismiss = onDismiss,
                        onNavigate = onNavigate,
                        modifier = modifier
                    )
                }
                if (showFill) { // section already gated by showMutatingActions
                    add(ElementType.FILL) { modifier ->
                        ManageGridItem(
                            elementType = ElementType.FILL,
                            onDismiss = onDismiss,
                            onNavigate = onNavigate,
                            modifier = modifier
                        )
                    }
                }
                if (showBatteryChange) {
                    add(ElementType.BATTERY_CHANGE) { modifier ->
                        ManageGridItem(
                            elementType = ElementType.BATTERY_CHANGE,
                            onDismiss = onDismiss,
                            onNavigate = onNavigate,
                            modifier = modifier
                        )
                    }
                }
                if (!isSimpleMode) { // section already gated by showMutatingActions
                    if (showCancelTempBasal) {
                        add(ElementType.TEMP_BASAL) { modifier ->
                            ManageGridItem(
                                text = cancelTempBasalText,
                                icon = IcTbrCancel,
                                color = ElementType.TEMP_BASAL.color(),
                                onDismiss = onDismiss,
                                onClick = onCancelTempBasalClick,
                                enabled = editingEnabled,
                                modifier = modifier
                            )
                        }
                    } else if (showTempBasal) {
                        add(ElementType.TEMP_BASAL) { modifier ->
                            ManageGridItem(
                                elementType = ElementType.TEMP_BASAL,
                                text = stringResource(CoreUiStrings.tempbasal_button),
                                onDismiss = onDismiss,
                                onNavigate = onNavigate,
                                modifier = modifier
                            )
                        }
                    }
                    if (showCancelExtendedBolus) {
                        add(ElementType.EXTENDED_BOLUS) { modifier ->
                            ManageGridItem(
                                text = cancelExtendedBolusText,
                                icon = IcCancelExtendedBolus,
                                color = ElementType.EXTENDED_BOLUS.color(),
                                onDismiss = onDismiss,
                                onClick = onCancelExtendedBolusClick,
                                enabled = editingEnabled,
                                modifier = modifier
                            )
                        }
                    } else if (showExtendedBolus) {
                        add(ElementType.EXTENDED_BOLUS) { modifier ->
                            ManageGridItem(
                                elementType = ElementType.EXTENDED_BOLUS,
                                text = stringResource(CoreUiStrings.extended_bolus_button),
                                onDismiss = onDismiss,
                                onNavigate = onNavigate,
                                modifier = modifier
                            )
                        }
                    }
                }
            }
        }

        // Section: Careportal (hidden in simple mode, collapsed by default). Its events now ride Client-Control
        // (Track B), so the section is also gated MASTER_OR_PAIRED_CLIENT — hidden on an unpaired client.
        val careportalAvailable = careportalElements.any { LocalClientRole.current.allows(it.role) }
        if (!isSimpleMode && showMutatingActions && careportalAvailable) {
            var careportalExpanded by remember { mutableStateOf(false) }
            val careportalExpandRequester = rememberBringIntoViewOnExpand(careportalExpanded)
            HorizontalDivider(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp))
            CollapsibleSectionHeader(
                text = stringResource(CoreUiStrings.careportal),
                expanded = careportalExpanded,
                onToggle = { careportalExpanded = !careportalExpanded }
            )

            AnimatedVisibility(
                visible = careportalExpanded,
                modifier = Modifier.bringIntoViewRequester(careportalExpandRequester)
            ) {
                GridSection(modifier = Modifier.padding(horizontal = 16.dp)) {
                    add(ElementType.BG_CHECK) { modifier ->
                        ManageGridItem(
                            elementType = ElementType.BG_CHECK,
                            onDismiss = onDismiss,
                            onNavigate = onNavigate,
                            coloredText = false,
                            modifier = modifier
                        )
                    }
                    add(ElementType.NOTE) { modifier ->
                        ManageGridItem(
                            elementType = ElementType.NOTE,
                            onDismiss = onDismiss,
                            onNavigate = onNavigate,
                            coloredText = false,
                            modifier = modifier
                        )
                    }
                    add(ElementType.EXERCISE) { modifier ->
                        ManageGridItem(
                            elementType = ElementType.EXERCISE,
                            onDismiss = onDismiss,
                            onNavigate = onNavigate,
                            coloredText = false,
                            modifier = modifier
                        )
                    }
                    add(ElementType.QUESTION) { modifier ->
                        ManageGridItem(
                            elementType = ElementType.QUESTION,
                            onDismiss = onDismiss,
                            onNavigate = onNavigate,
                            coloredText = false,
                            modifier = modifier
                        )
                    }
                    add(ElementType.ANNOUNCEMENT) { modifier ->
                        ManageGridItem(
                            elementType = ElementType.ANNOUNCEMENT,
                            onDismiss = onDismiss,
                            onNavigate = onNavigate,
                            coloredText = false,
                            modifier = modifier
                        )
                    }
                }
            }
        }

        // Section: Pump actions (only if non-empty)
        if (customActions.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp))
            SectionHeader(stringResource(CoreUiStrings.pump_actions))

            val pumpColor = ElementType.PUMP.color()
            GridSection(modifier = Modifier.padding(horizontal = 16.dp)) {
                customActions.forEach { action ->
                    add { modifier ->
                        ManageGridItem(
                            text = stringResource(action.name),
                            icon = action.icon,
                            color = pumpColor,
                            onDismiss = onDismiss,
                            onClick = { onCustomActionClick(action) },
                            modifier = modifier
                        )
                    }
                }
            }
        }
    }
}

/** "This phone is in read-only mode on the main phone": a chosen state, so a calm colour instead of the error one. */
@Composable
private fun ReadOnlyRoleBanner() {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = AapsSpacing.xxLarge, vertical = AapsSpacing.large),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium)
        ) {
            Icon(imageVector = Icons.Default.Visibility, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(
                text = stringResource(CoreUiStrings.client_read_only_banner),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

/** The careportal section's events; its collapsible header is shown only when the role leaves one of them. */
private val careportalElements = listOf(ElementType.BG_CHECK, ElementType.NOTE, ElementType.EXERCISE, ElementType.QUESTION, ElementType.ANNOUNCEMENT)

/**
 * Lays out composable items in a 2-column grid with equal-height rows. Items this phone's role cannot use are left out
 * before the layout (see [GridSectionScope.add]), so no gap is left; [header] is drawn only when an item is left.
 */
@Composable
private fun GridSection(
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
    content: GridSectionScope.() -> Unit
) {
    val items = GridSectionScope(LocalClientRole.current).apply(content).items
    if (items.isEmpty()) return
    header?.invoke()
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
    ) {
        items.chunked(2).forEach { rowItems ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max)
            ) {
                rowItems.forEach { item ->
                    item(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private class GridSectionScope(private val role: ClientRole) {

    val items = mutableListOf<@Composable (Modifier) -> Unit>()

    /** Adds [item], unless it opens [elementType] and this phone's role cannot use that (the master would refuse it). */
    fun add(elementType: ElementType? = null, item: @Composable (Modifier) -> Unit) {
        if (elementType == null || role.allows(elementType.role)) items.add(item)
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        // A heading, like its collapsible sibling below. This one does not expand, but a screen
        // reader skimming by heading should still stop on it.
        modifier = Modifier
            .semantics { heading() }
            .padding(horizontal = 24.dp, vertical = 12.dp)
    )
}

@Composable
private fun CollapsibleSectionHeader(text: String, expanded: Boolean, onToggle: () -> Unit) {
    val expandedState = stringResource(CoreUiStrings.state_expanded)
    val collapsedState = stringResource(CoreUiStrings.state_collapsed)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            // A heading, so a screen reader can jump between the sections of this sheet instead of
            // swiping through every row to reach the next one. The open/closed state rides with it,
            // so landing on the header says "Pump, collapsed" rather than leaving the user to guess.
            .semantics {
                heading()
                stateDescription = if (expanded) expandedState else collapsedState
            }
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Icon(
            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Grid card item that derives icon, color, label, and description from [ElementType].
 * Custom [text] overrides the ElementType label when provided.
 */
@Composable
private fun ManageGridItem(
    elementType: ElementType,
    onDismiss: () -> Unit,
    onNavigate: (NavigationRequest) -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
    coloredText: Boolean = true,
    enabled: Boolean = true
) {
    val color = elementType.color()
    val label = text ?: (stringResourceOrNull(elementType.label()) ?: "")
    val desc = elementType.description()
    val description = stringResourceOrNull(desc)
    // A relayed action (visible only to master/paired-client) is also DISABLED when the master is unreachable or
    // has remote control turned off — not just hidden when unpaired. Non-relayed entries (Pump, Pair-with-master)
    // use their own visibility lambda, so they stay enabled (the only thing you can still do is re-pair).
    val effectiveEnabled = enabled &&
        (masterEditingEnabled() || elementType.visibility != ElementVisibility.MASTER_OR_PAIRED_CLIENT)
    ManageGridItem(
        text = label,
        icon = elementType.icon(),
        color = color,
        onDismiss = onDismiss,
        onClick = { onNavigate(NavigationRequest.Element(elementType)) },
        description = description,
        coloredText = coloredText,
        enabled = effectiveEnabled,
        modifier = modifier
    )
}

@Composable
private fun ManageGridItem(
    text: String,
    icon: ImageVector,
    color: Color,
    onDismiss: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    coloredText: Boolean = true,
    enabled: Boolean = true
) {
    ElevatedCard(
        onClick = {
            onDismiss()
            onClick()
        },
        enabled = enabled,
        modifier = modifier
    ) {
        Column(modifier = Modifier
            .alpha(if (enabled) 1f else 0.38f)
            .padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SmallTonalIcon(icon = icon, color = color)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (coloredText) color else Color.Unspecified
                )
            }
            if (description != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SmallTonalIcon(icon: ImageVector, color: Color) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(20.dp)
            .background(color = color.copy(alpha = 0.12f), shape = CircleShape)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(12.dp)
        )
    }
}
