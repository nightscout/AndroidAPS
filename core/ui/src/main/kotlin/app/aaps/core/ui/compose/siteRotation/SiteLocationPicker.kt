package app.aaps.core.ui.compose.siteRotation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import app.aaps.core.ui.compose.LocalDateUtil
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MultiChoiceSegmentedButtonRow
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.aaps.core.data.model.TE
import app.aaps.core.ui.R
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.icons.IcCannulaChange
import app.aaps.core.ui.compose.icons.IcCgmInsert
import kotlinx.coroutines.launch

/**
 * Full site location picker with:
 * - Front + back body diagrams side by side (pinch-to-zoom, double-tap to reset)
 * - Pump/CGM filter toggles
 * - Arrow selection dialog
 * - Selected location label
 *
 * Pure composable: state-in, events-out, no ViewModel dependency.
 * Used in Fill/Care dialogs (as a screen), patch pump wizards (as a step),
 * and Site Rotation Management (for inline editing).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteLocationPicker(
    siteType: TE.Type,
    bodyType: BodyType,
    entries: List<TE>,
    selectedLocation: TE.Location,
    selectedArrow: TE.Arrow,
    onLocationSelected: (TE.Location) -> Unit,
    onArrowSelected: (TE.Arrow) -> Unit,
    modifier: Modifier = Modifier,
    selectedLocationString: String? = null
) {
    var showPumpSites by rememberSaveable { mutableStateOf(siteType == TE.Type.CANNULA_CHANGE) }
    var showCgmSites by rememberSaveable { mutableStateOf(siteType == TE.Type.SENSOR_CHANGE) }

    val isPumpType = siteType == TE.Type.CANNULA_CHANGE
    val isCgmType = siteType == TE.Type.SENSOR_CHANGE

    val effectiveShowPumpSites = isPumpType || showPumpSites
    val effectiveShowCgmSites = isCgmType || showCgmSites

    var showArrowDialog by rememberSaveable { mutableStateOf(false) }

    if (showArrowDialog) {
        ArrowSelectionDialog(
            onDismiss = { showArrowDialog = false },
            onArrowSelected = { arrow ->
                onArrowSelected(arrow)
                showArrowDialog = false
            }
        )
    }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Selected location label + arrow button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AapsSpacing.extraLarge, vertical = AapsSpacing.medium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (selectedLocation != TE.Location.NONE)
                    stringResource(R.string.selected_location, selectedLocationString ?: selectedLocation.text)
                else
                    stringResource(R.string.select_location),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = { showArrowDialog = true },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = selectedArrow.directionToComposeIcon(),
                    contentDescription = stringResource(R.string.select_arrow),
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AapsSpacing.extraLarge, vertical = AapsSpacing.medium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MultiChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                SegmentedButton(
                    checked = effectiveShowPumpSites,
                    onCheckedChange = { if (!isPumpType) showPumpSites = it },
                    enabled = !isPumpType,
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                    icon = {}
                ) {
                    Icon(
                        imageVector = IcCannulaChange,
                        contentDescription = stringResource(R.string.careportal_pump_site_management),
                        modifier = Modifier.size(24.dp)
                    )
                }
                SegmentedButton(
                    checked = effectiveShowCgmSites,
                    onCheckedChange = { if (!isCgmType) showCgmSites = it },
                    enabled = !isCgmType,
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                    icon = {}
                ) {
                    Icon(
                        imageVector = IcCgmInsert,
                        contentDescription = stringResource(R.string.careportal_cgm_site_management),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            val tooltipState = remember { TooltipState() }
            val scope = rememberCoroutineScope()
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                tooltip = {
                    PlainTooltip {
                        Text(stringResource(R.string.site_filter_info))
                    }
                },
                state = tooltipState
            ) {
                IconButton(
                    onClick = { scope.launch { tooltipState.show() } },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        val filteredEntries = entries.filter { te ->
            when (te.type) {
                TE.Type.CANNULA_CHANGE -> effectiveShowPumpSites
                TE.Type.SENSOR_CHANGE -> effectiveShowCgmSites
                else -> false
            }
        }

        // Body diagram (zoomable, front + back)
        Box(
            modifier = Modifier
                .weight(2f)
                .fillMaxWidth()
        ) {
            ZoomableBodyDiagram(
                filteredLocationColor = filteredEntries,
                showPumpSites = effectiveShowPumpSites,
                showCgmSites = effectiveShowCgmSites,
                selectedLocation = selectedLocation,
                bodyType = bodyType,
                onZoneClick = onLocationSelected,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AapsSpacing.extraLarge),
                editedType = siteType
            )
        }

        // Filtered entry list
        if (filteredEntries.isNotEmpty()) {
            val dateUtil = LocalDateUtil.current
            val locationFiltered = if (selectedLocation != TE.Location.NONE)
                filteredEntries.filter { it.location == selectedLocation }
            else
                filteredEntries
            val displayEntries = remember(locationFiltered, dateUtil) {
                locationFiltered.map { te ->
                    SiteEntryDisplayData(
                        typeIcon = if (te.type == TE.Type.CANNULA_CHANGE) IcCannulaChange else IcCgmInsert,
                        dateString = dateUtil.dateStringShort(te.timestamp),
                        locationString = (te.location ?: TE.Location.NONE).text,
                        arrowIcon = (te.arrow ?: TE.Arrow.NONE).directionToComposeIcon(),
                        note = te.note,
                        timestamp = te.timestamp,
                        location = te.location ?: TE.Location.NONE
                    )
                }
            }
            SiteEntryList(
                entries = displayEntries,
                showEditButton = false,
                onEntryClick = { onLocationSelected(it.location) },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
    }
}

/**
 * Extended version of [SiteLocationPicker] with filter toggles for both pump and CGM sites.
 * Used in Management screen where both types can be shown simultaneously.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteLocationPickerWithFilters(
    bodyType: BodyType,
    entries: List<TE>,
    showPumpSites: Boolean,
    showCgmSites: Boolean,
    selectedLocation: TE.Location,
    onLocationSelected: (TE.Location) -> Unit,
    onShowPumpSites: (Boolean) -> Unit,
    onShowCgmSites: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    editedType: TE.Type? = null
) {
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Filter toggles
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AapsSpacing.extraLarge, vertical = AapsSpacing.medium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MultiChoiceSegmentedButtonRow(
                modifier = Modifier.weight(1f)
            ) {
                SegmentedButton(
                    checked = showPumpSites,
                    onCheckedChange = { onShowPumpSites(!showPumpSites) },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                    icon = {}
                ) {
                    Icon(
                        imageVector = IcCannulaChange,
                        contentDescription = stringResource(R.string.careportal_pump_site_management),
                        modifier = Modifier.size(24.dp)
                    )
                }
                SegmentedButton(
                    checked = showCgmSites,
                    onCheckedChange = { onShowCgmSites(!showCgmSites) },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                    icon = {}
                ) {
                    Icon(
                        imageVector = IcCgmInsert,
                        contentDescription = stringResource(R.string.careportal_cgm_site_management),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            val tooltipState = remember { TooltipState() }
            val scope = rememberCoroutineScope()
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                tooltip = {
                    PlainTooltip {
                        Text(stringResource(R.string.site_filter_info))
                    }
                },
                state = tooltipState
            ) {
                IconButton(
                    onClick = { scope.launch { tooltipState.show() } },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Body diagram (zoomable, front + back)
        ZoomableBodyDiagram(
            filteredLocationColor = entries.filter { te ->
                when (te.type) {
                    TE.Type.CANNULA_CHANGE -> showPumpSites
                    TE.Type.SENSOR_CHANGE  -> showCgmSites
                    else                   -> false
                }
            },
            showPumpSites = showPumpSites,
            showCgmSites = showCgmSites,
            selectedLocation = selectedLocation,
            bodyType = bodyType,
            onZoneClick = onLocationSelected,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = AapsSpacing.extraLarge),
            editedType = editedType
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SiteLocationPickerPreview() {
    MaterialTheme {
        SiteLocationPicker(
            siteType = TE.Type.CANNULA_CHANGE,
            bodyType = BodyType.MAN,
            entries = emptyList(),
            selectedLocation = TE.Location.FRONT_RIGHT_UPPER_ABDOMEN,
            selectedArrow = TE.Arrow.UP,
            onLocationSelected = {},
            onArrowSelected = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SiteLocationPickerWithFiltersPreview() {
    MaterialTheme {
        SiteLocationPickerWithFilters(
            bodyType = BodyType.MAN,
            entries = emptyList(),
            showPumpSites = true,
            showCgmSites = true,
            selectedLocation = TE.Location.NONE,
            onLocationSelected = {},
            onShowPumpSites = {},
            onShowCgmSites = {}
        )
    }
}
