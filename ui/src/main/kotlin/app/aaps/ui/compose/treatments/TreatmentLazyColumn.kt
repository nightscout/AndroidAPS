package app.aaps.ui.compose.treatments

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.ui.compose.LocalDateUtil

/**
 * Generic lazy column for treatment screens with sticky date headers and item animations.
 * Minimal implementation - only extracts the sticky header pattern.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T> TreatmentLazyColumn(
    items: List<T>,
    getTimestamp: (T) -> Long,
    getItemKey: (T) -> Any,
    rh: ResourceHelper,
    itemContent: @Composable (T) -> Unit,
    modifier: Modifier = Modifier
) {
    val dateUtil = LocalDateUtil.current
    val groupedByDay by remember(items) {
        derivedStateOf {
            items.groupBy { item ->
                dateUtil.dateString(getTimestamp(item))
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        groupedByDay.forEach { (dateString, itemsForDay) ->
            stickyHeader(key = "header_$dateString") {
                Text(
                    text = dateUtil.dateStringRelative(getTimestamp(itemsForDay.first()), rh),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            items(
                items = itemsForDay,
                key = getItemKey
            ) { item ->
                Box(modifier = Modifier.animateItem()) {
                    itemContent(item)
                }
            }
        }
    }
}
