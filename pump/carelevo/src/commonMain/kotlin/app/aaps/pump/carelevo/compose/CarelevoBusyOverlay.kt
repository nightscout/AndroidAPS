package app.aaps.pump.carelevo.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import app.aaps.core.ui.compose.AapsSpacing

/**
 * Covers the screen while a patch command runs: dims it, takes every tap, and says what it waits for.
 *
 * The spinner and the text sit on a card of their own. They used to be drawn straight over the
 * screen, and the dimming is weak on the dark theme, so the text ran into the buttons under it -
 * "Priming 1 of 3" was read on top of Retry, seen on an iPhone in dark mode.
 */
@Composable
internal fun CarelevoBusyOverlay(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
            // Takes all taps: without this the dimming is only decoration, and a second tap could
            // reach (and fire again) the button under it while the first Bluetooth command runs.
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.padding(horizontal = AapsSpacing.xxLarge)
        ) {
            Column(
                modifier = Modifier.padding(AapsSpacing.xxLarge),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(AapsSpacing.extraLarge)
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Text(
                    text = text,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
