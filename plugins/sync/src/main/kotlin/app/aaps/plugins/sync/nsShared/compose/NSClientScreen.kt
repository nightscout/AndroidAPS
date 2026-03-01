package app.aaps.plugins.sync.nsShared.compose

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.interfaces.nsclient.NSClientLog
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.plugins.sync.R
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val jsonPrettyPrint = Json { prettyPrint = true }
private val timeFormatPreview = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

private const val JSON_EXPANDED = "json_expanded"
private const val JSON_COLLAPSED = "json_collapsed"

@Composable
fun NSClientScreen(
    viewModel: NSClientViewModel,
    dateUtil: DateUtil,
    title: String,
    setToolbarConfig: (ToolbarConfig) -> Unit,
    onNavigateBack: () -> Unit,
    onPauseChanged: (Boolean) -> Unit,
    onClearLog: () -> Unit,
    onSendNow: () -> Unit,
    onFullSync: () -> Unit,
    onSettings: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        setToolbarConfig(
            ToolbarConfig(
                title = title,
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(app.aaps.core.ui.R.string.back)
                        )
                    }
                },
                actions = {
                    if (onSettings != null) {
                        IconButton(onClick = onSettings) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = stringResource(app.aaps.core.ui.R.string.nav_plugin_preferences)
                            )
                        }
                    }
                    NSClientMenu(
                        onClearLog = onClearLog,
                        onSendNow = onSendNow,
                        onFullSync = onFullSync
                    )
                }
            )
        )
    }

    NSClientScreenContent(
        uiState = uiState,
        dateUtil = dateUtil,
        onPauseChanged = onPauseChanged,
        modifier = modifier
    )
}

@Composable
fun NSClientScreenContent(
    uiState: NSClientUiState,
    dateUtil: DateUtil? = null,
    onPauseChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(AapsSpacing.extraLarge),
        verticalArrangement = Arrangement.spacedBy(AapsSpacing.small)
    ) {
        // URL row - only URL text is clickable
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AapsSpacing.small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.ns_client_url),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            ClickableUrlText(url = uiState.url)
        }

        // Status and Queue with Pause button on right
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AapsSpacing.small)
            ) {
                LabelValueRow(label = stringResource(R.string.status), value = uiState.status)
                LabelValueRow(label = stringResource(R.string.queue), value = uiState.queue)
            }

            Row(
                modifier = Modifier
                    .padding(start = AapsSpacing.extraLarge)
                    .align(Alignment.CenterVertically),
                horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(if (uiState.paused) app.aaps.core.ui.R.string.paused else app.aaps.core.ui.R.string.running),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Switch(
                    checked = !uiState.paused,
                    onCheckedChange = { isRunning -> onPauseChanged(!isRunning) }
                )
            }
        }

        HorizontalDivider()

        // Logs
        val listState = rememberLazyListState()

        // Auto-scroll to top when new log arrives
        LaunchedEffect(uiState.logList.firstOrNull()?.date) {
            if (uiState.logList.isNotEmpty()) {
                listState.scrollToItem(0)
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(AapsSpacing.extraSmall)
        ) {
            items(
                items = uiState.logList,
                key = { it.id }
            ) { log ->
                var isJsonExpanded by remember { mutableStateOf(false) }
                var isOverflowing by remember(log) { mutableStateOf(false) }

                if (isOverflowing) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = buildAnnotatedString {
                                append(dateUtil?.timeStringWithSeconds(log.date) ?: timeFormatPreview.format(Instant.ofEpochMilli(log.date)))
                                append(" ")
                                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                    append(log.action)
                                }
                            },
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface)
                        )

                        val bodyText = buildAnnotatedString {
                            append(log.logText ?: "")
                            append(" ")
                            log.json?.let { json ->
                                if (isJsonExpanded) {
                                    pushStringAnnotation(JSON_EXPANDED, annotation = JSON_EXPANDED)
                                    withStyle(style = SpanStyle(fontFamily = FontFamily.Monospace)) {
                                        append("\n" + jsonPrettyPrint.encodeToString(JsonElement.serializer(), json))
                                    }
                                    pop()
                                } else {
                                    pushStringAnnotation(JSON_COLLAPSED, annotation = JSON_COLLAPSED)
                                    withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline)) {
                                        append("{...}")
                                    }
                                    pop()
                                }
                            }
                        }
                        ClickableAnnotatedText(
                            text = bodyText,
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
                            modifier = Modifier.padding(start = AapsSpacing.extraLarge),
                            onClick = { offset ->
                                if (bodyText.getStringAnnotations(JSON_COLLAPSED, offset, offset).any()) {
                                    isJsonExpanded = true
                                } else if (bodyText.getStringAnnotations(JSON_EXPANDED, offset, offset).any()) {
                                    isJsonExpanded = false
                                    isOverflowing = false
                                }
                            }
                        )
                    }
                } else {
                    val fullText = buildAnnotatedString {
                        dateUtil?.let { append(it.timeStringWithSeconds(log.date)) }
                            ?: append(log.date.toString())
                        append(" ")
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(log.action)
                        }
                        append(" ")
                        append(log.logText ?: "")
                        append(" ")
                        log.json?.let {
                            pushStringAnnotation(JSON_COLLAPSED, annotation = JSON_COLLAPSED)
                            withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline)) {
                                append("{...}")
                            }
                            pop()
                        }
                    }
                    ClickableAnnotatedText(
                        text = fullText,
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        onTextLayout = { textLayoutResult ->
                            if (textLayoutResult.hasVisualOverflow) {
                                isOverflowing = true
                            }
                        },
                        onClick = { offset ->
                            if (fullText.getStringAnnotations(JSON_COLLAPSED, offset, offset).any()) {
                                isJsonExpanded = true
                                isOverflowing = true
                            }
                        }
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun NSClientScreenPreview() {
    MaterialTheme {
        NSClientScreenContent(
            uiState = NSClientUiState(
                url = "https://nightscout.example.com",
                status = "Connected",
                queue = "0",
                paused = false,
                logList = listOf(
                    NSClientLog(action = "UPLOAD", logText = "Uploading treatments"),
                    NSClientLog(action = "READ", logText = "Reading entries"),
                    NSClientLog(action = "SYNC", logText = "Synchronization complete"),
                )
            )
        )
    }
}

@Composable
private fun ClickableAnnotatedText(
    text: AnnotatedString,
    style: TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    onClick: (Int) -> Unit
) {
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    BasicText(
        text = text,
        style = style,
        maxLines = maxLines,
        overflow = overflow,
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures { position ->
                    layoutResult?.let { layout ->
                        val offset = layout.getOffsetForPosition(position)
                        onClick(offset)
                    }
                }
            },
        onTextLayout = { layoutResult = it; onTextLayout?.invoke(it) }
    )
}

@Composable
private fun ClickableUrlText(
    url: String,
    modifier: Modifier = Modifier
) {
    if (url.isNotEmpty() && url.startsWith("http", ignoreCase = true)) {

        Text(
            buildAnnotatedString {
                withLink(LinkAnnotation.Url(url = url)) { append(url) }
            },
            style = MaterialTheme.typography.bodyMedium,
            modifier = modifier
        )
    } else {
        Text(
            text = url,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = modifier
        )
    }
}

@Composable
private fun LabelValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun NSClientMenu(
    onClearLog: () -> Unit,
    onSendNow: () -> Unit,
    onFullSync: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { showMenu = true }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = stringResource(app.aaps.core.ui.R.string.more_options)
            )
        }
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.clear_log)) },
                onClick = {
                    showMenu = false
                    onClearLog()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.deliver_now)) },
                onClick = {
                    showMenu = false
                    onSendNow()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.full_sync)) },
                onClick = {
                    showMenu = false
                    onFullSync()
                }
            )
        }
    }
}
