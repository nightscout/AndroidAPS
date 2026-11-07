package app.aaps.plugins.source.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import app.aaps.core.interfaces.source.StagingState
import app.aaps.core.ui.compose.AapsSpacing

/**
 * What a state means for the user, stripped of protocol detail.
 *
 * The driver has seven phases and the staging slot four states; the user only needs to know whether
 * something works, is busy, is waiting, or has stopped. Colour then carries that meaning, so the
 * screen can be read at a glance instead of word by word.
 */
enum class CgmUiState {

    /** Doing its job — session up, warm-up done, staging ready to promote. */
    Ready,

    /** Busy and progressing normally — pairing, connecting, warming up. */
    Working,

    /** Nothing wrong, nothing happening yet — idle, no staging sensor. */
    Waiting,

    /** Stopped and needs the user. */
    Failed,
}

/**
 * Maps a warm-up phase to what it means for the user.
 *
 * The driver maps its phases onto [CgmWarmupPhase] first, so this one function keeps the chip and
 * the ring driver-agnostic.
 */
fun CgmWarmupPhase.toUiState(): CgmUiState = when (this) {
    CgmWarmupPhase.READY        -> CgmUiState.Ready
    CgmWarmupPhase.FAILED       -> CgmUiState.Failed
    CgmWarmupPhase.IDLE         -> CgmUiState.Waiting
    CgmWarmupPhase.PAIRING,
    CgmWarmupPhase.CONNECTING,
    CgmWarmupPhase.RECONNECTING,
    CgmWarmupPhase.WARMING      -> CgmUiState.Working
}

/** Maps a staging slot state to what it means for the user. */
fun StagingState.toUiState(): CgmUiState = when (this) {
    StagingState.ABSENT   -> CgmUiState.Waiting
    StagingState.WARMUP,
    StagingState.SETTLING -> CgmUiState.Working
    StagingState.READY    -> CgmUiState.Ready
}

/**
 * Small state badge: a coloured dot plus its label.
 *
 * Deliberately not an `AssistChip` — this shows state, it is not a control, so it must not look or
 * behave like something that can be pressed. The dot and the label are read as one phrase by
 * TalkBack rather than as two separate items.
 */
@Composable
fun CgmStateChip(
    state: CgmUiState,
    label: String,
    modifier: Modifier = Modifier,
) {
    val container: Color
    val content: Color
    when (state) {
        CgmUiState.Ready   -> {
            container = MaterialTheme.colorScheme.primaryContainer
            content = MaterialTheme.colorScheme.onPrimaryContainer
        }

        CgmUiState.Working -> {
            container = MaterialTheme.colorScheme.tertiaryContainer
            content = MaterialTheme.colorScheme.onTertiaryContainer
        }

        CgmUiState.Waiting -> {
            container = MaterialTheme.colorScheme.surfaceVariant
            content = MaterialTheme.colorScheme.onSurfaceVariant
        }

        CgmUiState.Failed  -> {
            container = MaterialTheme.colorScheme.errorContainer
            content = MaterialTheme.colorScheme.onErrorContainer
        }
    }
    Row(
        modifier = modifier
            .clearAndSetSemantics { contentDescription = label }
            .background(color = container, shape = RoundedCornerShape(AapsSpacing.chipCornerRadius))
            .padding(horizontal = AapsSpacing.medium, vertical = AapsSpacing.small),
        horizontalArrangement = Arrangement.spacedBy(AapsSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(AapsSpacing.medium)
                .background(color = content, shape = CircleShape),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = content,
        )
    }
}
