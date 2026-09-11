package app.aaps.core.ui.compose.pickers

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import app.aaps.core.ui.compose.LocalDateUtil
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs

private const val VISIBLE_ITEMS = 5
private const val ITEM_HEIGHT_DP = 44

@Composable
fun HourWheelPicker(
    selectedHour: Int,
    availableHours: List<Int>,
    onHourSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateUtil = LocalDateUtil.current
    val listState = rememberLazyListState()
    val initialIndex = availableHours.indexOf(selectedHour).coerceAtLeast(0)
    val snapFlingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    val itemHeightPx = with(LocalDensity.current) { ITEM_HEIGHT_DP.dp.toPx() }
    val halfVisibleItems = VISIBLE_ITEMS / 2

    // Scroll to initial position (accounting for padding items)
    LaunchedEffect(selectedHour, availableHours) {
        if (initialIndex >= 0) {
            listState.scrollToItem(initialIndex)
        }
    }

    // Calculate centered item based on actual viewport position
    val centeredItemIndex by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) return@derivedStateOf initialIndex

            val viewportCenter = layoutInfo.viewportStartOffset + layoutInfo.viewportSize.height / 2

            // Find the item closest to center, accounting for padding items
            val centeredItem = visibleItems.minByOrNull {
                abs(it.offset + it.size / 2 - viewportCenter)
            }

            // Adjust index to account for padding items at top
            val rawIndex = centeredItem?.index ?: initialIndex
            (rawIndex - halfVisibleItems).coerceIn(0, availableHours.lastIndex)
        }
    }

    // Notify selection when scrolling stops
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { isScrolling ->
                if (!isScrolling && centeredItemIndex in availableHours.indices) {
                    onHourSelected(availableHours[centeredItemIndex])
                }
            }
    }

    Popup(
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Surface(
            modifier = modifier.width(130.dp),
            shape = RoundedCornerShape(12.dp),
            shadowElevation = 8.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Box(
                modifier = Modifier.height((ITEM_HEIGHT_DP * VISIBLE_ITEMS).dp),
                contentAlignment = Alignment.Center
            ) {
                // Selection indicator - prominent bordered surface
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ITEM_HEIGHT_DP.dp)
                        .padding(horizontal = 8.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    border = BorderStroke(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                ) {}

                LazyColumn(
                    state = listState,
                    modifier = Modifier.height((ITEM_HEIGHT_DP * VISIBLE_ITEMS).dp),
                    flingBehavior = snapFlingBehavior,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Top padding items for centering
                    items(halfVisibleItems) {
                        Box(modifier = Modifier.height(ITEM_HEIGHT_DP.dp))
                    }

                    itemsIndexed(availableHours) { index, hour ->
                        // Calculate alpha based on actual visual distance
                        val layoutInfo = listState.layoutInfo
                        val viewportCenter = layoutInfo.viewportStartOffset + layoutInfo.viewportSize.height / 2
                        val itemInfo = layoutInfo.visibleItemsInfo.find { it.index == index + halfVisibleItems }

                        val alpha = if (itemInfo != null) {
                            val itemCenter = itemInfo.offset + itemInfo.size / 2
                            val distanceFromCenter = abs(itemCenter - viewportCenter)
                            val maxDistance = itemHeightPx * 2.5f
                            // Smooth alpha gradient: 1.0 at center, 0.2 at edges
                            (1f - (distanceFromCenter / maxDistance).coerceIn(0f, 1f) * 0.8f)
                        } else {
                            0.2f
                        }

                        val isSelected = index == centeredItemIndex
                        val hourLabel = dateUtil.timeStringFromSeconds(hour * 3600)

                        Box(
                            modifier = Modifier
                                .height(ITEM_HEIGHT_DP.dp)
                                .fillMaxWidth()
                                .clickable {
                                    onHourSelected(hour)
                                    onDismiss()
                                }
                                .alpha(alpha),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = hourLabel,
                                fontSize = 18.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    // Bottom padding items for centering
                    items(halfVisibleItems) {
                        Box(modifier = Modifier.height(ITEM_HEIGHT_DP.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun HourWheelPickerDialog(
    selectedHour: Int,
    minHour: Int = 0,
    maxHour: Int = 23,
    excludeHour: Int? = null,
    onHourSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val availableHours = remember(minHour, maxHour, excludeHour) {
        (minHour..maxHour).filter { it != excludeHour }
    }

    HourWheelPicker(
        selectedHour = selectedHour,
        availableHours = availableHours,
        onHourSelected = onHourSelected,
        onDismiss = onDismiss
    )
}
