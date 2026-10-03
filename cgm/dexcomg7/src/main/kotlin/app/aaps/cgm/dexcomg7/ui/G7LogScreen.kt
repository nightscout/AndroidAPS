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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.ViewModel
import app.aaps.core.interfaces.maintenance.FileListProvider
import app.aaps.core.interfaces.resources.ResourceHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
    private val context: Context,
    private val commLog: G7CommLog,
    private val dateUtil: DateUtil,
    private val fileListProvider: FileListProvider,
    private val rh: ResourceHelper
) : ViewModel() {

    val entries = commLog.entries

    private val _message = MutableStateFlow<String?>(null)

    /** The result of the last save, for the line under the buttons. */
    val message: StateFlow<String?> = _message.asStateFlow()

    fun format(entry: G7CommLog.Entry): String = "${dateUtil.timeStringWithSeconds(entry.time)}  ${entry.text}"

    /**
     * With the date on each line, since the log covers 24 hours. A fixed form, so saved logs look the
     * same whatever language the app had when they were saved.
     */
    fun asText(): String {
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        return commLog.asText { format.format(Date(it)) }
    }

    fun clear() {
        commLog.clear()
        _message.value = null
    }

    /**
     * Writes the whole log to a text file in the `exports` folder of the AAPS directory (the one chosen
     * in Maintenance), where it is easy to share from. Runs off the main thread.
     */
    suspend fun saveToFile() {
        _message.value = withContext(Dispatchers.IO) {
            runCatching {
                val dir = fileListProvider.ensureExportDirExists() ?: return@runCatching rh.gs(R.string.dexcom_g7_log_no_directory)
                val name = "DexcomG7_log_" + SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).format(Date()) + ".txt"
                val file = dir.createFile("text/plain", name) ?: return@runCatching rh.gs(R.string.dexcom_g7_log_save_failed)
                context.contentResolver.openOutputStream(file.uri)?.use { it.write(asText().toByteArray()) }
                    ?: return@runCatching rh.gs(R.string.dexcom_g7_log_save_failed)
                rh.gs(R.string.dexcom_g7_log_saved, "exports/" + (file.name ?: name))
            }.getOrElse { rh.gs(R.string.dexcom_g7_log_save_failed) }
        }
    }
}

/**
 * What passed between the phone and the sensor in the last 24 hours, newest first. Copy puts it on the
 * clipboard; Save writes it to a file in the AAPS exports folder, for sharing. It never contains the
 * pairing code or a key.
 */
@Composable
fun G7LogScreen(viewModel: G7LogViewModel) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Column(modifier = Modifier.fillMaxSize().padding(AapsSpacing.extraLarge)) {
        Row(horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Dexcom G7", viewModel.asText()))
            }) { Text(stringResource(R.string.dexcom_g7_copy_log)) }
            OutlinedButton(onClick = { scope.launch { viewModel.saveToFile() } }) { Text(stringResource(R.string.dexcom_g7_save_log)) }
            OutlinedButton(onClick = viewModel::clear) { Text(stringResource(R.string.dexcom_g7_clear_log)) }
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = AapsSpacing.medium)) }
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
