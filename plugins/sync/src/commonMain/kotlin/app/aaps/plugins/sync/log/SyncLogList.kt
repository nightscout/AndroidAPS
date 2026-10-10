package app.aaps.plugins.sync.log

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import app.aaps.core.interfaces.sync.SyncLogEntry
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.ui.compose.AapsSpacing
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.time.Instant

private val jsonPrettyPrint = Json { prettyPrint = true }

private const val JSON_EXPANDED = "json_expanded"
private const val JSON_COLLAPSED = "json_collapsed"

/**
 * The log of a sync plugin (Nightscout, xDrip, Tidepool), newest line on top. It scrolls to the top when
 * a new line arrives. A line is one row; a line that does not fit is shown in full on more rows, and a
 * line with JSON has a `{...}` link that opens it.
 *
 * @param dateUtil null only in previews and tests
 */
@Composable
fun SyncLogList(
    entries: List<SyncLogEntry>,
    dateUtil: DateUtil?,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // Auto-scroll to top when a new line arrives
    LaunchedEffect(entries.firstOrNull()?.id) {
        if (entries.isNotEmpty()) listState.scrollToItem(0)
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(AapsSpacing.extraSmall)
    ) {
        items(items = entries, key = { it.id }) { entry ->
            SyncLogRow(entry, dateUtil)
        }
    }
}

@Composable
private fun SyncLogRow(entry: SyncLogEntry, dateUtil: DateUtil?) {
    var isJsonExpanded by remember { mutableStateOf(false) }
    var isOverflowing by remember(entry) { mutableStateOf(false) }
    val time = dateUtil?.timeStringWithSeconds(entry.date) ?: previewTime(entry.date)
    val style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface)
    val linkStyle = SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline)

    if (isOverflowing) {
        // Too long for one row: time and action on top, the rest in full below
        Column(modifier = Modifier.fillMaxWidth()) {
            BasicText(
                text = buildAnnotatedString {
                    append(time)
                    append(" ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(entry.action) }
                },
                style = style
            )
            val bodyText = buildAnnotatedString {
                entry.text?.let { append(it) }
                entry.json?.let { json ->
                    if (isJsonExpanded) {
                        pushStringAnnotation(JSON_EXPANDED, annotation = JSON_EXPANDED)
                        withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
                            append("\n" + jsonPrettyPrint.encodeToString(JsonElement.serializer(), json))
                        }
                        pop()
                    } else {
                        append(" ")
                        pushStringAnnotation(JSON_COLLAPSED, annotation = JSON_COLLAPSED)
                        withStyle(linkStyle) { append("{...}") }
                        pop()
                    }
                }
            }
            if (bodyText.isNotEmpty())
                ClickableAnnotatedText(
                    text = bodyText,
                    style = style,
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
            append(time)
            append(" ")
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(entry.action) }
            entry.text?.let {
                append(" ")
                append(it)
            }
            entry.json?.let {
                append(" ")
                pushStringAnnotation(JSON_COLLAPSED, annotation = JSON_COLLAPSED)
                withStyle(linkStyle) { append("{...}") }
                pop()
            }
        }
        ClickableAnnotatedText(
            text = fullText,
            style = style,
            modifier = Modifier.fillMaxWidth(),
            maxLines = 1,
            overflow = TextOverflow.Clip,
            onTextLayout = { textLayoutResult ->
                if (textLayoutResult.hasVisualOverflow) isOverflowing = true
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
                    layoutResult?.let { layout -> onClick(layout.getOffsetForPosition(position)) }
                }
            },
        onTextLayout = { layoutResult = it; onTextLayout?.invoke(it) }
    )
}

// Only used by Compose previews and tests, where there is no DateUtil. Hand-padded because
// String.format is JVM only.
private fun previewTime(millis: Long): String =
    Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault()).let {
        "${it.hour.toString().padStart(2, '0')}:${it.minute.toString().padStart(2, '0')}:${it.second.toString().padStart(2, '0')}"
    }
