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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.plugins.sync.R
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

private val jsonPrettyPrint = Json { prettyPrint = true }

private const val JSON_EXPANDED = "json_expanded"
private const val JSON_COLLAPSED = "json_collapsed"

@Composable
fun NSClientScreen(
    viewModel: NSClientViewModel,
    dateUtil: DateUtil,
    rh: ResourceHelper,
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

    // Set up toolbar
    LaunchedEffect(Unit) {
        setToolbarConfig(
            ToolbarConfig(
                title = title,
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = rh.gs(app.aaps.core.ui.R.string.back)
                        )
                    }
                },
                actions = {
                    // Settings button first (more accessible)
                    if (onSettings != null) {
                        IconButton(onClick = onSettings) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = rh.gs(app.aaps.core.ui.R.string.nav_plugin_preferences)
                            )
                        }
                    }
                    // Overflow menu at far right (Material Design convention)
                    NSClientMenu(
                        rh = rh,
                        onClearLog = onClearLog,
                        onSendNow = onSendNow,
                        onFullSync = onFullSync
                    )
                }
            )
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // URL row - only URL text is clickable
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
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
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                LabelValueRow(label = stringResource(R.string.status), value = uiState.status)
                LabelValueRow(label = stringResource(R.string.queue), value = uiState.queue)
            }

            Row(
                modifier = Modifier
                    .padding(start = 16.dp)
                    .align(Alignment.CenterVertically),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
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
            verticalArrangement = Arrangement.spacedBy(2.dp)
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
                                append(dateUtil.timeStringWithSeconds(log.date))
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
                            modifier = Modifier.padding(start = 16.dp),
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
                        append(dateUtil.timeStringWithSeconds(log.date))
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
fun ClickableAnnotatedText(
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
        horizontalArrangement = Arrangement.spacedBy(8.dp)
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
    rh: ResourceHelper,
    onClearLog: () -> Unit,
    onSendNow: () -> Unit,
    onFullSync: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { showMenu = true }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = rh.gs(app.aaps.core.ui.R.string.more_options)
            )
        }
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text(rh.gs(R.string.clear_log)) },
                onClick = {
                    showMenu = false
                    onClearLog()
                }
            )
            DropdownMenuItem(
                text = { Text(rh.gs(R.string.deliver_now)) },
                onClick = {
                    showMenu = false
                    onSendNow()
                }
            )
            DropdownMenuItem(
                text = { Text(rh.gs(R.string.full_sync)) },
                onClick = {
                    showMenu = false
                    onFullSync()
                }
            )
        }
    }
}
