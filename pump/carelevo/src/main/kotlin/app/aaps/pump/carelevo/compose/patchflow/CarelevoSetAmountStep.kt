package app.aaps.pump.carelevo.compose.patchflow

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.aaps.core.ui.R as CoreUiR
import app.aaps.core.ui.compose.pump.WizardButton
import app.aaps.core.ui.compose.pump.WizardStepLayout
import app.aaps.pump.carelevo.R
import app.aaps.pump.carelevo.config.FillConfig
import app.aaps.pump.carelevo.presentation.viewmodel.CarelevoPatchConnectionFlowViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Wizard step for choosing the insulin fill amount. Replaces the former bottom sheet so the choice
 * stays on the wizard rails (progress indicator, Next/Cancel). The wheel picker is non-scrolling
 * content, so [WizardStepLayout] is used with `scrollable = false`.
 */
@Composable
internal fun CarelevoSetAmountStep(
    viewModel: CarelevoPatchConnectionFlowViewModel
) {
    var selectedValue by remember { mutableIntStateOf(viewModel.inputInsulin) }
    val values = remember { (FillConfig.FILL_MIN_UNITS..FillConfig.FILL_MAX_UNITS step FillConfig.FILL_STEP_UNITS).toList() }

    WizardStepLayout(
        primaryButton = WizardButton(
            text = stringResource(CoreUiR.string.next),
            onClick = { viewModel.confirmAmount(selectedValue) }
        ),
        secondaryButton = WizardButton(
            text = stringResource(CoreUiR.string.cancel),
            onClick = { viewModel.exitWizard() }
        ),
        scrollable = false
    ) {
        Text(
            text = stringResource(R.string.patch_prepare_dialog_msg_insulin_range, FillConfig.FILL_MIN_UNITS, FillConfig.FILL_MAX_UNITS),
            style = MaterialTheme.typography.bodyMedium
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(WheelItemHeight * WheelVisibleRows)
        ) {
            CarelevoWheelPicker(
                values = values,
                selectedValue = selectedValue,
                onValueSelected = { selectedValue = it }
            )
        }
    }
}

private val WheelItemHeight = 52.dp
private const val WheelVisibleRows = 5

@Composable
private fun CarelevoWheelPicker(
    values: List<Int>,
    selectedValue: Int,
    onValueSelected: (Int) -> Unit
) {
    val initialIndex = remember(values, selectedValue) {
        values.indexOf(selectedValue).coerceAtLeast(0)
    }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialIndex
    )
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    val centerRowIndex = WheelVisibleRows / 2
    val verticalContentPadding = WheelItemHeight * centerRowIndex
    val coroutineScope = rememberCoroutineScope()
    var centeredIndex by remember(values, selectedValue) { mutableIntStateOf(initialIndex) }

    LaunchedEffect(listState, values) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo }
            .filter { it.isNotEmpty() }
            .map { visibleItems ->
                val viewportCenter = (listState.layoutInfo.viewportStartOffset + listState.layoutInfo.viewportEndOffset) / 2
                visibleItems.minByOrNull { item ->
                    abs((item.offset + item.size / 2) - viewportCenter)
                }?.index?.coerceIn(0, values.lastIndex) ?: centeredIndex
            }
            .distinctUntilChanged()
            .collect { selectedIndex ->
                centeredIndex = selectedIndex
                onValueSelected(values[selectedIndex])
            }
    }

    LaunchedEffect(selectedValue, values) {
        val selectedIndex = values.indexOf(selectedValue)
        if (selectedIndex >= 0 && selectedIndex != centeredIndex) {
            centeredIndex = selectedIndex
            listState.animateScrollToItem(selectedIndex)
        }
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        LazyColumn(
            state = listState,
            flingBehavior = flingBehavior,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = verticalContentPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            itemsIndexed(values) { index, value ->
                val isSelected = index == centeredIndex
                WheelRow(
                    value = value,
                    isSelected = isSelected,
                    onClick = {
                        coroutineScope.launch {
                            centeredIndex = index
                            listState.animateScrollToItem(index)
                        }
                    }
                )
            }
        }

        WheelSelectionOverlay()
    }
}

@Composable
private fun WheelRow(
    value: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(WheelItemHeight)
            .padding(horizontal = 24.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .background(
                if (isSelected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                } else {
                    Color.Transparent
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = value.toString(),
            style = if (isSelected) {
                MaterialTheme.typography.headlineSmall
            } else {
                MaterialTheme.typography.titleMedium
            },
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun WheelSelectionOverlay() {
    val overlayHeight = WheelItemHeight
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(overlayHeight)
            .padding(horizontal = 24.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f))
    ) {
        HorizontalDivider(modifier = Modifier.align(Alignment.TopCenter))
        HorizontalDivider(modifier = Modifier.align(Alignment.BottomCenter))
    }
}
