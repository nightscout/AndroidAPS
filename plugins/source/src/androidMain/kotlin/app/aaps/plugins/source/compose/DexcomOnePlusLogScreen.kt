package app.aaps.plugins.source.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.stringResource
import app.aaps.plugins.source.DexcomOnePlusHost
import app.aaps.plugins.source.SourceStrings
import kotlinx.coroutines.delay

/** How often the log screen reads the lines again while it is open. */
private const val LOG_REFRESH_MILLIS = 5_000L

/**
 * What the ONE+ / G7 driver wrote, read only.
 *
 * The lines come from [DexcomOnePlusHost.driverLog], the driver lines of `AndroidAPS.log`.
 * [DexcomOnePlusHost.DriverLog.note] says where they come from, so a person knows what they are
 * reading. The screen follows the end of the log while
 * the person is there and stops following once they scroll up.
 *
 * [reloads] is bumped by the refresh button in the toolbar.
 *
 * The idea follows `G7CommLog`, the communication log of the G7 plugin tab in nightscout/AndroidAPS
 * pull request 5397 (Dexcom G7 direct, by t1dude, commit `bec1b16ba20`), written again on this
 * driver. See the NOTICE file of `:plugins:dexcom_oneplus`.
 */
@Composable
fun DexcomOnePlusLogScreen(
    host: DexcomOnePlusHost,
    reloads: Int,
    onLinesChanged: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var log by remember { mutableStateOf<DexcomOnePlusHost.DriverLog?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(reloads) {
        while (true) {
            val read = host.driverLog()
            log = read
            onLinesChanged(read.lines)
            delay(LOG_REFRESH_MILLIS)
        }
    }
    val lines = log?.lines.orEmpty()
    LaunchedEffect(lines.size) {
        val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
        val following = last == null || last >= lines.size - 3
        if (following && lines.isNotEmpty()) listState.scrollToItem(lines.lastIndex)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = AapsSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(AapsSpacing.small),
    ) {
        log?.let { current ->
            Text(
                text = stringResource(current.note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (lines.isEmpty()) {
            Text(stringResource(SourceStrings.cgm_driver_log_empty))
        } else {
            Text(
                text = stringResource(SourceStrings.cgm_driver_log_count, lines.size),
                style = MaterialTheme.typography.labelMedium,
            )
            SelectionContainer {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(lines) { line ->
                        // Long lines wrap rather than run off the side: a driver line can be a
                        // whole hex frame, and a phone is narrow.
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
