package app.aaps.ui.compose.components

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.aaps.ui.R

/**
 * Container for screen content that handles loading and empty states.
 * Uses [Crossfade] for smooth transitions between loading, empty, and content states.
 */
@Composable
fun ContentContainer(
    isLoading: Boolean,
    isEmpty: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val state = when {
        isLoading -> ContentState.Loading
        isEmpty   -> ContentState.Empty
        else      -> ContentState.Content
    }

    Crossfade(
        targetState = state,
        modifier = modifier.fillMaxSize(),
        label = "ContentContainer"
    ) { targetState ->
        when (targetState) {
            ContentState.Loading -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }

            ContentState.Empty   -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.SearchOff,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        Text(
                            text = stringResource(R.string.no_records_available),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            ContentState.Content -> content()
        }
    }
}

private enum class ContentState { Loading, Empty, Content }
