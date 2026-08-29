package app.aaps.plugins.sync.nsclientV3.compose

import app.aaps.core.ui.compose.stringResource
import app.aaps.core.ui.CoreUiStrings
import app.aaps.plugins.sync.SyncStrings
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.ToolbarConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private val jsonPrettyPrint = Json { prettyPrint = true }

// Only used by Compose previews, where there is no DateUtil. Hand-padded because String.format is
// JVM only.
private fun previewTime(millis: Long): String =
    Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault()).let {
        "${it.hour.toString().padStart(2, '0')}:${it.minute.toString().padStart(2, '0')}:${it.second.toString().padStart(2, '0')}"
    }

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
                            contentDescription = stringResource(CoreUiStrings.back)
                        )
                    }
                },
                actions = {
                    if (onSettings != null) {
                        IconButton(onClick = onSettings) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = stringResource(CoreUiStrings.nav_plugin_preferences)
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

/**
 * @see NSClientScreenPreview
 */
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
                text = stringResource(SyncStrings.ns_client_url),
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
                LabelValueRow(label = stringResource(SyncStrings.status_label), value = uiState.status)
                LabelValueRow(label = stringResource(SyncStrings.queue), value = uiState.queue)
            }

            Row(
                modifier = Modifier
                    .padding(start = AapsSpacing.extraLarge)
                    .align(Alignment.CenterVertically),
                horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(if (uiState.paused) CoreUiStrings.paused else CoreUiStrings.running),
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
                                append(dateUtil?.timeStringWithSeconds(log.date) ?: previewTime(log.date))
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
                contentDescription = stringResource(CoreUiStrings.more_options)
            )
        }
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(SyncStrings.clear_log)) },
                onClick = {
                    showMenu = false
                    onClearLog()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(SyncStrings.deliver_now)) },
                onClick = {
                    showMenu = false
                    onSendNow()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(SyncStrings.full_sync)) },
                onClick = {
                    showMenu = false
                    onFullSync()
                }
            )
        }
    }
}
