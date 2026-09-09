package app.aaps.ui.compose.aboutDialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import app.aaps.core.interfaces.configuration.ExternalOptions
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.LocalAppIcon
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.UiStrings

data class AboutDialogData(
    val title: String,
    val message: String,
    val enabledOptions: List<ExternalOptions> = emptyList()
)

/**
 * @see AboutAlertDialogPreview
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AboutAlertDialog(
    data: AboutDialogData,
    /**
     * Null where there is no such problem, and then no button is drawn.
     *
     * Only Android lets the device maker kill a background app, and the page this opens is that
     * site's page for the maker of the current device. Off Android it built an address for a
     * manufacturer the site has never heard of - see `Config.oemBackgroundKilling`.
     */
    onOpenBatteryHelp: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    val annotatedMessage = buildClickableMessage(data.message)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            LocalAppIcon.current(Modifier.size(48.dp))
        },
        title = {
            Text(
                text = data.title,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = annotatedMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                if (data.enabledOptions.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(0.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy((-4).dp),
                    ) {
                        data.enabledOptions.forEach { option ->
                            AssistChip(
                                onClick = {},
                                label = { Text(option.filename, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(CoreUiStrings.ok))
            }
        },
        dismissButton = onOpenBatteryHelp?.let { openHelp ->
            {
                TextButton(onClick = openHelp) {
                    Text(stringResource(CoreUiStrings.cta_dont_kill_my_app_info))
                }
            }
        },
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
    )
}

@Composable
private fun buildClickableMessage(message: String): AnnotatedString {
    return buildAnnotatedString {
        val urlPattern = Regex("https?://[^\\s]+")
        var lastIndex = 0

        urlPattern.findAll(message).forEach { matchResult ->
            append(message.substring(lastIndex, matchResult.range.first))

            val url = matchResult.value
            withLink(LinkAnnotation.Url(url)) {
                withStyle(
                    style = SpanStyle(
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline
                    )
                ) {
                    append(url)
                }
            }

            lastIndex = matchResult.range.last + 1
        }

        if (lastIndex < message.length) {
            append(message.substring(lastIndex))
        }
    }
}
