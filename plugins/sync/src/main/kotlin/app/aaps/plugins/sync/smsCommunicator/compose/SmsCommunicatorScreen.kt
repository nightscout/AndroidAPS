package app.aaps.plugins.sync.smsCommunicator.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.compose.AapsSpacing

@Composable
internal fun SmsCommunicatorScreen(
    viewModel: SmsCommunicatorViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SmsCommunicatorScreenContent(uiState = uiState, modifier = modifier)
}

@Composable
internal fun SmsCommunicatorScreenContent(
    uiState: SmsCommunicatorUiState,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when messages change
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.scrollToItem(uiState.messages.size - 1)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .padding(AapsSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(AapsSpacing.extraSmall)
    ) {
        itemsIndexed(uiState.messages) { _, sms ->
            SmsLogItem(sms = sms)
        }
    }
}

@Composable
private fun SmsLogItem(
    sms: SmsItem,
    modifier: Modifier = Modifier
) {
    val directionArrow = when {
        sms.isReceived -> "<<<"
        sms.isSent     -> ">>>"
        else           -> ""
    }

    val statusIndicator = when {
        sms.isIgnored   -> "\u2591"
        sms.isProcessed -> "\u25CF"
        else            -> "\u25CB"
    }

    Text(
        text = buildAnnotatedString {
            append("${sms.time} $directionArrow $statusIndicator ${sms.phoneNumber} ")
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(sms.text)
            }
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.fillMaxWidth()
    )
}

@Preview(showBackground = true)
@Composable
private fun SmsCommunicatorScreenPreview() {
    MaterialTheme {
        SmsCommunicatorScreenContent(
            uiState = SmsCommunicatorUiState(
                messages = listOf(
                    SmsItem(time = "12:00", phoneNumber = "+1234567890", text = "BG", isReceived = true, isSent = false, isProcessed = true, isIgnored = false),
                    SmsItem(time = "12:00", phoneNumber = "+1234567890", text = "BG: 5.5 mmol/l", isReceived = false, isSent = true, isProcessed = true, isIgnored = false),
                    SmsItem(time = "12:05", phoneNumber = "+9876543210", text = "BOLUS 1.5", isReceived = true, isSent = false, isProcessed = false, isIgnored = false),
                    SmsItem(time = "12:10", phoneNumber = "+1111111111", text = "LOOP STATUS", isReceived = true, isSent = false, isProcessed = false, isIgnored = true),
                )
            )
        )
    }
}
