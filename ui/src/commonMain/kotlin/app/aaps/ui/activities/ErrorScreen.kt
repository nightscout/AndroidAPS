package app.aaps.ui.activities

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.UiStrings

/**
 * Modal-style screen rendered over a scrim when an urgent alarm fires. Displays the alarm title/status and three
 * actions, each saying what it does: mute for 5 minutes (+5 min for the "time to eat" timer), silence, and silence
 * and close.
 *
 * Shared code, because iOS needs the same screen: `ErrorActivity` hosts it on Android, and the Compose host of the
 * iOS app shows it for a pump alert through [AlertScreen]. Nothing in here is platform specific any more; the one
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
    /**
     * A pump alert: the lengths its silence may have, shown as a row to pick from. Null: no row, and the two silence
     * buttons say nothing of time, as for one of the app's own alarms, which ends when it is answered.
     */
    silenceLengths: List<Int>? = null,
    /** The length picked in the row, in minutes; the two silence buttons use it. */
    silenceMinutes: Int = 0,
    onSilenceMinutes: (Int) -> Unit = {},
    /**
     * Makes alerts of this kind silent on this phone from now on, for a kind the user may silence
     * (`_docs/ALARMS.md`, section 6). Null: no such button, as for a protected kind or an alarm that is no alert.
     */
    onSilenceKind: (() -> Unit)? = null,
    /**
     * "Silence everywhere" of a BG alarm (`_docs/ALARMS.md`, step 2f): silences it for the picked length here at once
     * and on every other phone, and closes. Null: not offered, as for an alarm of this phone only or with no link.
     */
    onSilenceEverywhere: (() -> Unit)? = null,
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

                // How long the two buttons below silence it, the alert's default picked
                if (silenceLengths != null) {
                    Text(
                        text = stringResource(CoreUiStrings.alarm_silence_length),
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    // Three in a row: five do not fit on a phone, and a last one alone on its line looks lost
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium, Alignment.CenterHorizontally),
                        maxItemsInEachRow = 3,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        silenceLengths.forEach { minutes ->
                            FilterChip(
                                selected = minutes == silenceMinutes,
                                onClick = { onSilenceMinutes(minutes) },
                                label = { Text(silenceLength(minutes)) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(AapsSpacing.medium))
                }

                // Silence button: the sound stops, this screen stays open
                Button(
                    onClick = onMute,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (silenceLengths != null) stringResource(CoreUiStrings.alarm_silence_for, silenceLength(silenceMinutes))
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
                        if (silenceLengths != null) stringResource(CoreUiStrings.alarm_silence_for_and_close, silenceLength(silenceMinutes))
                        else stringResource(CoreUiStrings.alarm_silence_and_close)
                    )
                }

                // The same answer for every phone: here at once, then for the others
                if (onSilenceEverywhere != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onSilenceEverywhere,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(UiStrings.alarm_silence_everywhere_for_and_close, silenceLength(silenceMinutes)))
                    }
                }

                // A setting, not an answer to this alarm: from now on a card with no sound, on this phone
                if (onSilenceKind != null) {
                    Spacer(modifier = Modifier.height(AapsSpacing.large))
                    TextButton(
                        onClick = onSilenceKind,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(UiStrings.alert_kind_silence_on_this_phone))
                    }
                }
            }
        }
    }
}

/** A silence length as the user reads it: "15 min", or "2 h" from a full hour on. */
@Composable
private fun silenceLength(minutes: Int): String =
    if (minutes >= 60 && minutes % 60 == 0) stringResource(CoreUiStrings.format_hours_only, minutes / 60)
    else stringResource(CoreUiStrings.format_mins, minutes)
