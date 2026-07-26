package app.aaps.ui.activities

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.aaps.core.ui.compose.ExcludeFromJacocoGeneratedReport

/**
 * Modal-style screen rendered over a scrim by [ErrorActivity] when an urgent alarm fires.
 * Displays the alarm title/status and three actions: OK (dismiss), Mute, Mute 5 min.
 *
 * Audio (MediaPlayer + volume ramp) is owned by [ErrorActivity] itself — the [onStart]
 * callback fires once on first composition so the activity can begin playback when the
 * screen is shown.
 */
@Composable
fun ErrorScreen(
    title: String,
    status: String,
    appIcon: Int,
    onOk: () -> Unit,
    onMute: () -> Unit,
    onMute5Min: () -> Unit,
    onStart: () -> Unit
) {
    DisposableEffect(Unit) {
        onStart()
        onDispose { }
    }

    // Semi-transparent background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(enabled = false) { }
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        // Dialog-style card
        Card(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Error icon with app icon badge
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    // Main error icon
                    Image(
                        painter = painterResource(id = app.aaps.core.ui.R.drawable.ic_error_red_48dp),
                        contentDescription = null,
                        modifier = Modifier.size(48.dp)
                    )
                    // App icon badge in bottom-right corner
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(24.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surface,
                                shape = CircleShape
                            )
                            .padding(2.dp)
                    ) {
                        Image(
                            painter = painterResource(id = appIcon),
                            contentDescription = "App icon",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Title
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Status message
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Mute 5 min button
                Button(
                    onClick = onMute5Min,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(app.aaps.core.ui.R.string.mute5min))
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Mute button
                Button(
                    onClick = onMute,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(app.aaps.core.ui.R.string.mute))
                }

                Spacer(modifier = Modifier.height(12.dp))

                // OK button
                Button(
                    onClick = onOk,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(app.aaps.core.ui.R.string.ok))
                }
            }
        }
    }
}

@ExcludeFromJacocoGeneratedReport
@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun ErrorScreenPreview() {
    MaterialTheme {
        ErrorScreen(
            title = "Pump unreachable",
            status = "Last successful communication 25 minutes ago. Check Bluetooth and pump status.",
            appIcon = app.aaps.core.ui.R.drawable.ic_error_red_48dp,
            onOk = {},
            onMute = {},
            onMute5Min = {},
            onStart = {}
        )
    }
}

@ExcludeFromJacocoGeneratedReport
@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun ErrorScreenShortPreview() {
    MaterialTheme {
        ErrorScreen(
            title = "Bolus error",
            status = "Delivery failed.",
            appIcon = app.aaps.core.ui.R.drawable.ic_error_red_48dp,
            onOk = {},
            onMute = {},
            onMute5Min = {},
            onStart = {}
        )
    }
}
