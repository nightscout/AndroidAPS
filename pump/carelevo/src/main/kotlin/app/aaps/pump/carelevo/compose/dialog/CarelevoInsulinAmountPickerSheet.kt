package app.aaps.pump.carelevo.compose.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import app.aaps.pump.carelevo.R
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CarelevoInsulinAmountPickerSheet(
    initialValue: Int,
    onDismissRequest: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var selectedValue by remember(initialValue) { mutableIntStateOf(initialValue) }
    val values = remember { (50..300 step 10).toList() }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState
    ) {

        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.patch_prepare_dialog_title_insulin_amount),
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = stringResource(R.string.patch_prepare_dialog_msg_insulin_range),
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(top = 12.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onDismissRequest,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = stringResource(R.string.carelevo_btn_cancel))
                }
                Button(
                    onClick = { onConfirm(selectedValue) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = stringResource(R.string.common_btn_ok))
                }
            }
        }
    }
}

private val WheelItemHeight = 52.dp
private val WheelVisibleRows = 5

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
