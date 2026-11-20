package app.aaps.core.ui.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign

/**
 * One tab of [PluginTabsContent].
 *
 * @param title the label on the tab
 * @param content what the tab shows. It may set the toolbar: only the selected tab is on screen, so it owns the
 *        toolbar while it is selected, and a tab chosen again sets it again.
 */
class PluginTab(val title: String, val content: @Composable () -> Unit)

/**
 * Tabs on top of a plugin's own screen, with the look of the statistics screen. Made for CGM drivers (the
 * device's status and controls on the first tab, its BG list on the next), and open to any plugin screen.
 *
 * No swipe between tabs, unlike statistics: each tab may own the toolbar (the BG list has its own selection mode),
 * and a swipe would show two tabs at once.
 */
@Composable
fun PluginTabsContent(tabs: List<PluginTab>, modifier: Modifier = Modifier) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val current = selected.coerceIn(tabs.indices)
    Column(modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = current) {
            tabs.forEachIndexed { index, tab ->
                Tab(
                    selected = current == index,
                    onClick = { selected = index },
                    text = { Text(text = tab.title, maxLines = 2, textAlign = TextAlign.Center) }
                )
            }
        }
        Box(Modifier.fillMaxSize()) {
            // A new composition per tab, so the tab's effects (its toolbar first) run again when it is chosen
            key(current) { tabs[current].content() }
        }
    }
}
