package app.aaps.cgm.dexcomg7.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.cgm.dexcomg7.R
import app.aaps.cgm.dexcomg7.data.G7CommLog
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.ui.compose.AapsSpacing
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey

@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
@Stable
@Inject
class G7LogViewModel(
    private val commLog: G7CommLog,
    private val dateUtil: DateUtil
) : ViewModel() {

    val entries = commLog.entries

    fun format(entry: G7CommLog.Entry): String = "${dateUtil.timeStringWithSeconds(entry.time)}  ${entry.text}"

    fun asText(): String = commLog.entries.value.joinToString("\n") { format(it) }

    fun clear() = commLog.clear()
}

/**
 * What passed between the phone and the sensor, newest first. Copy puts it on the clipboard so it can
 * be pasted into a bug report. It never contains the pairing code or a key.
 */
@Composable
fun G7LogScreen(viewModel: G7LogViewModel) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxSize().padding(AapsSpacing.extraLarge)) {
        Row(horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Dexcom G7", viewModel.asText()))
            }) { Text(stringResource(R.string.dexcom_g7_copy_log)) }
            OutlinedButton(onClick = viewModel::clear) { Text(stringResource(R.string.dexcom_g7_clear_log)) }
        }
        if (entries.isEmpty()) {
            Text(stringResource(R.string.dexcom_g7_log_empty), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = AapsSpacing.large))
        }
        SelectionContainer {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(top = AapsSpacing.large)) {
                items(entries.asReversed()) { entry ->
                    Text(
                        viewModel.format(entry),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(vertical = AapsSpacing.extraSmall)
                    )
                }
            }
        }
    }
}
