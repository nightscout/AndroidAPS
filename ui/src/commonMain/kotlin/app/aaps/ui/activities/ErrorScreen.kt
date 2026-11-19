package app.aaps.ui.activities

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.stringResource

/**
 * Modal-style screen rendered over a scrim when an urgent alarm fires. Displays the alarm title/status and three
 * actions, each saying what it does: mute for 5 minutes (+5 min for the "time to eat" timer), silence, and silence
 * and close.
 *
 * Shared code, because iOS needs the same screen: `ErrorActivity` hosts it on Android, and the Compose host of the
 * iOS app shows it for a pump alert through [PumpAlertScreen]. Nothing in here is platform specific any more; the one
 * thing that was - the app icon, which is a drawable id on Android - comes in as [badge] instead.
 *
 * It owns no audio. Every alarm is a card in the notification list and that list is the only sound owner, so this
 * screen is a view of the ringing card. It used to take an `onStart` callback for the host to begin playback with,
 * which was the second alarm path - see `_docs/PUMP_DESIGN.md`, section 8.
 *
 * @see ErrorScreenPreview
 * @see ErrorScreenShortPreview
 */
@Composable
fun ErrorScreen(
    title: String,
    status: String,
    onOk: () -> Unit,
    onMute: () -> Unit,
    onMute5Min: () -> Unit,
    /**
     * Whether "Mute 5 min" is offered. False for an alarm whose own action is already the delay - the "time to eat"
     * timer, where "+5 min" moves the timer instead.
     */
    showMute5Min: Boolean = true,
    /** A pump alert's own step, for example "clear the alarm on the patch", shown first. Null: no such button. */
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    /** A pump alert: its silence lasts this long, and the two silence buttons say so. Null: they say nothing of time. */
    silenceMinutes: Int? = null,
    /**
     * Drawn small over the error icon, to say which AAPS this is. Android passes its flavour launcher icon, which is
     * a drawable id and so cannot be a parameter of shared code. Null: no badge, which is what iOS shows.
     */
    badge: (@Composable () -> Unit)? = null
) {
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
                    Icon(
                        imageVector = Icons.Filled.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(48.dp)
                    )
                    // App icon badge in bottom-right corner
                    if (badge != null) Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(24.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surface,
                                shape = CircleShape
                            )
                            .padding(2.dp)
                    ) {
                        badge()
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

                // A pump alert's own step: the button that acts on the alarm, not only on the sound
                if (actionLabel != null) {
                    Button(
                        onClick = onAction,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(actionLabel)
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Mute 5 min: the alarm goes quiet and comes back in five minutes
                if (showMute5Min) {
                    Button(
                        onClick = onMute5Min,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(CoreUiStrings.mute5min))
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Silence button: the sound stops, this screen stays open
                Button(
                    onClick = onMute,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (silenceMinutes != null) stringResource(CoreUiStrings.alarm_silence_for, stringResource(CoreUiStrings.format_mins, silenceMinutes))
                        else stringResource(CoreUiStrings.alarm_silence)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Silence and close button
                Button(
                    onClick = onOk,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (silenceMinutes != null) stringResource(CoreUiStrings.alarm_silence_for_and_close, stringResource(CoreUiStrings.format_mins, silenceMinutes))
                        else stringResource(CoreUiStrings.alarm_silence_and_close)
                    )
                }
            }
        }
    }
}
