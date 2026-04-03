package app.aaps.core.ui.compose.dialogs

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.aaps.core.ui.R
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.SnackbarColors
import app.aaps.core.ui.compose.SnackbarMessage

/**
 * Custom visuals that carry the message type so the styling lambda can read it
 * from [snackbarData.visuals] instead of the outer recomposition-sensitive parameter.
 */
private class TypedSnackbarVisuals(
    override val message: String,
    val type: SnackbarMessage,
    override val withDismissAction: Boolean = true,
    override val actionLabel: String? = null,
    override val duration: SnackbarDuration = SnackbarDuration.Short
) : SnackbarVisuals

/**
 * Snackbar host that displays typed messages with appropriate styling.
 * Supports Error, Warning, Info, and Success message types with distinct colors and icons.
 *
 * **Usage:**
 * ```kotlin
 * AapsSnackbarHost(
 *     message = uiState.error?.let { SnackbarMessage.Error(it) },
 *     onDismiss = { viewModel.clearSnackbar() },
 *     modifier = Modifier.align(Alignment.BottomCenter)
 * )
 * ```
 *
 * @param message The message to display, or null if no message
 * @param onDismiss Callback when message is dismissed
 * @param modifier Modifier for the snackbar host
 */
@Composable
fun AapsSnackbarHost(
    message: SnackbarMessage?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val colors = AapsTheme.snackbarColors

    LaunchedEffect(message) {
        message?.let {
            // showSnackbar suspends until the snackbar is dismissed or timed out.
            // The message type is captured in TypedSnackbarVisuals so the styling
            // lambda reads it from snackbarData — not from the outer `message` param
            // which may become null if the caller clears it early.
            snackbarHostState.showSnackbar(TypedSnackbarVisuals(message = it.message, type = it))
            onDismiss()
        }
    }

    SnackbarHost(
        hostState = snackbarHostState,
        modifier = modifier
    ) { snackbarData ->
        val visuals = snackbarData.visuals
        val type = (visuals as? TypedSnackbarVisuals)?.type

        val (containerColor, contentColor, icon) = resolveStyle(type, colors)

        Snackbar(
            containerColor = containerColor,
            contentColor = contentColor,
            dismissAction = {
                TextButton(onClick = { snackbarHostState.currentSnackbarData?.dismiss() }) {
                    Text(
                        text = stringResource(R.string.dismiss),
                        color = contentColor
                    )
                }
            }
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = contentColor
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = visuals.message,
                    color = contentColor
                )
            }
        }
    }
}

@Composable
private fun resolveStyle(
    type: SnackbarMessage?,
    colors: SnackbarColors
): Triple<Color, Color, ImageVector> =
    when (type) {
        is SnackbarMessage.Error -> Triple(colors.errorContainer, colors.onErrorContainer, Icons.Default.Error)
        is SnackbarMessage.Warning -> Triple(colors.warningContainer, colors.onWarningContainer, Icons.Default.Warning)
        is SnackbarMessage.Info -> Triple(colors.infoContainer, colors.onInfoContainer, Icons.Default.Info)
        is SnackbarMessage.Success -> Triple(colors.successContainer, colors.onSuccessContainer, Icons.Default.CheckCircle)
        null -> Triple(
            MaterialTheme.colorScheme.inverseSurface,
            MaterialTheme.colorScheme.inverseOnSurface,
            Icons.Default.Info
        )
    }
