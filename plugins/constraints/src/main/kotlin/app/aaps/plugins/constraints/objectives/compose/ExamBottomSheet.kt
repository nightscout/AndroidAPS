package app.aaps.plugins.constraints.objectives.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.aaps.plugins.constraints.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamBottomSheet(
    state: ExamSheetState,
    onOptionToggle: (Int) -> Unit,
    onVerify: () -> Unit,
    onReset: () -> Unit,
    onNavigate: (Int) -> Unit,
    onNextUnanswered: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Progress header
            Text(
                text = stringResource(R.string.objectives_exam_question_progress, state.currentTaskIndex + 1, state.totalTasks),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { (state.currentTaskIndex + 1).toFloat() / state.totalTasks },
                modifier = Modifier.fillMaxWidth(),
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Task name
            Text(
                text = state.taskName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // Question
            if (state.question.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = state.question,
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Options
            state.options.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = option.isChecked,
                        onCheckedChange = { onOptionToggle(option.index) },
                        enabled = !state.isAnswered
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = option.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = when {
                            state.isAnswered && option.isCorrect -> MaterialTheme.colorScheme.primary
                            state.isAnswered && !option.isCorrect -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }

            // Hints
            if (state.hints.isNotEmpty()) {
                val uriHandler = LocalUriHandler.current
                val urlRegex = remember { Regex("https?://\\S+") }
                Spacer(modifier = Modifier.height(8.dp))
                state.hints.forEach { hint ->
                    val url = urlRegex.find(hint.text)?.value
                    Text(
                        text = hint.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (url != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = if (url != null) {
                            Modifier
                                .clickable { uriHandler.openUri(url) }
                                .padding(vertical = 2.dp)
                        } else {
                            Modifier.padding(vertical = 2.dp)
                        }
                    )
                }
            }

            // Disabled until message
            state.disabledUntil?.let { disabledText ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = disabledText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                if (state.isAnswered) {
                    OutlinedButton(onClick = onReset) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.height(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.objectives_exam_reset))
                    }
                } else {
                    Button(
                        onClick = onVerify,
                        enabled = state.canAnswer
                    ) {
                        Text(stringResource(R.string.objectives_button_verify))
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Navigation buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { onNavigate(-1) },
                    enabled = state.canGoBack
                ) {
                    Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = stringResource(R.string.objectives_exam_previous))
                }

                FilledTonalButton(
                    onClick = onNextUnanswered,
                    enabled = !state.allCompleted
                ) {
                    Icon(Icons.Default.SkipNext, contentDescription = null, modifier = Modifier.height(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.objectives_exam_next_unanswered),
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                IconButton(
                    onClick = { onNavigate(1) },
                    enabled = state.canGoNext
                ) {
                    Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = stringResource(R.string.objectives_exam_next))
                }
            }
        }
    }
}
