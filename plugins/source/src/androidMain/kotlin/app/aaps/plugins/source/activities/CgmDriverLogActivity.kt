package app.aaps.plugins.source.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.interfaces.logging.LoggerUtils
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.AapsTopAppBar
import app.aaps.core.ui.compose.LocalPreferences
import app.aaps.core.ui.compose.MetroAppCompatActivity
import app.aaps.plugins.source.R
import app.aaps.plugins.source.logs.DriverLogReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import dev.zacsweers.metro.Inject

/**
 * Shows what a native CGM driver has written, without leaving the app.
 *
 * The point is a user who has a sensor that does not work. Their lines are in `AndroidAPS.log`
 * already, but reaching them means exporting the whole log folder and opening a five megabyte file
 * on a phone. This screen reads the same file, keeps only the driver lines, and lets them be read
 * or shared as they are.
 *
 * The screen knows no driver. The caller says which logger names to show, and may give a title,
 * through [intent]. Without logger names the screen shows nothing.
 *
 * It is read only. Nothing here talks to a sensor.
 */
class CgmDriverLogActivity : MetroAppCompatActivity() {

    @Inject lateinit var preferences: Preferences

    @Inject lateinit var loggerUtils: LoggerUtils

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Other apps may not draw over this screen: an overlay could hide what a tap really does, here on a
        // sensor (start, pre-soak, switch). Same as ComposeMainActivity. Needs HIDE_OVERLAY_WINDOWS.
        window.setHideOverlayWindows(true)
        setContent {
            CompositionLocalProvider(LocalPreferences provides preferences) {
                AapsTheme {
                    CgmDriverLogScreen(
                        logDirectory = loggerUtils.logDirectory,
                        loggerNames = loggerNamesOf(intent),
                        title = intent.getStringExtra(EXTRA_TITLE),
                        onBack = { finish() },
                    )
                }
            }
        }
    }

    companion object {

        /** The logger names whose lines are shown, as a `String[]`. */
        const val EXTRA_LOGGER_NAMES = "app.aaps.plugins.source.CGM_DRIVER_LOG_LOGGER_NAMES"

        /** The title of the screen, as a `String`. Optional: the generic title is used without it. */
        const val EXTRA_TITLE = "app.aaps.plugins.source.CGM_DRIVER_LOG_TITLE"

        /**
         * The intent that opens this screen for one driver.
         *
         * @param loggerNames the logger names of the driver, for example its log tag.
         * @param title the screen title, or null for the generic one.
         */
        fun intent(context: Context, loggerNames: Set<String>, title: String? = null): Intent =
            Intent(context, CgmDriverLogActivity::class.java)
                .putExtra(EXTRA_LOGGER_NAMES, loggerNames.toTypedArray())
                .apply { if (title != null) putExtra(EXTRA_TITLE, title) }

        /** The logger names an [intent] carries. Empty when it carries none. */
        fun loggerNamesOf(intent: Intent): Set<String> =
            intent.getStringArrayExtra(EXTRA_LOGGER_NAMES)?.toSet() ?: emptySet()
    }
}

/** How much text a share may carry, so a long session cannot break the send. */
private const val MAX_SHARED_CHARACTERS = 200_000

/** How often the screen goes back to the file while it is open. */
private const val REFRESH_MILLIS = 5_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CgmDriverLogScreen(
    logDirectory: String,
    loggerNames: Set<String>,
    title: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var lines by remember { mutableStateOf(emptyList<String>()) }
    var reloads by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()

    LaunchedEffect(reloads) {
        while (true) {
            lines = withContext(Dispatchers.IO) { DriverLogReader.read(logDirectory, loggerNames) }
            delay(REFRESH_MILLIS)
        }
    }

    // Follow the end of the log while the user is already there, and leave them alone once they
    // have scrolled up to read something.
    val atEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf true
            last >= lines.size - 2
        }
    }
    LaunchedEffect(lines) {
        if (atEnd && lines.isNotEmpty()) listState.scrollToItem(lines.lastIndex)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            AapsTopAppBar(
                title = { Text(title ?: stringResource(R.string.cgm_driver_log_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cgm_nav_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { reloads++ }) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.cgm_driver_log_refresh),
                        )
                    }
                    IconButton(
                        enabled = lines.isNotEmpty(),
                        onClick = {
                            val text = lines.joinToString("\n").takeLast(MAX_SHARED_CHARACTERS)
                            val send = Intent(Intent.ACTION_SEND)
                                .setType("text/plain")
                                .putExtra(Intent.EXTRA_TEXT, text)
                            context.startActivity(
                                Intent.createChooser(send, context.getString(R.string.cgm_driver_log_share))
                            )
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = stringResource(R.string.cgm_driver_log_share),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = AapsSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(AapsSpacing.small),
        ) {
            if (lines.isEmpty()) {
                Text(stringResource(R.string.cgm_driver_log_empty))
            } else {
                // How many lines, and whether the screen is still following the end of the file.
                // "Is it still moving?" is the question this screen is opened to answer.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.cgm_driver_log_count, lines.size),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (atEnd) {
                        Text(
                            text = stringResource(R.string.cgm_driver_log_follow),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                SelectionContainer {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(lines) { line ->
                            // Long lines wrap rather than run off the side. A phone is narrow and
                            // a driver line can be a whole hex frame; wrapping is the only way to
                            // read one without dragging it across the screen.
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
}

@Preview
@Composable
private fun CgmDriverLogScreenPreview() {
    MaterialTheme {
        CgmDriverLogScreen(logDirectory = "", loggerNames = emptySet(), title = null, onBack = {})
    }
}
