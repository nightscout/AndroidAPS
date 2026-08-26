package app.aaps.pump.carelevo.compose.alarm

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.aaps.core.ui.R as CoreUiR
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.pump.carelevo.R

internal data class CarelevoAlarmUiModel(
    val appIcon: Int,
    val title: String,
    val content: String,
    val primaryButtonText: String,
    val muteButtonText: String,
    val mute5minButtonText: String
)

@Composable
internal fun CarelevoAlarmScreen(
    alarm: CarelevoAlarmUiModel?,
    onPrimaryClick: () -> Unit,
    onMuteClick: () -> Unit,
    onMute5MinClick: () -> Unit
) {
    if (alarm == null) return

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Blocking modal scrim: consume all taps so nothing underneath is reachable
                // while a critical alarm is presented.
                .pointerInput(Unit) { detectTapGestures { } }
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .padding(horizontal = AapsSpacing.xxLarge)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(AapsSpacing.extraLarge),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(AapsSpacing.xxLarge),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Image(
                            painter = painterResource(id = CoreUiR.drawable.ic_error_red_48dp),
                            contentDescription = null,
                            modifier = Modifier.size(48.dp)
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(AapsSpacing.xxLarge)
                                .background(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = CircleShape
                                )
                                .padding(2.dp)
                        ) {
                            Image(
                                painter = painterResource(id = alarm.appIcon),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(AapsSpacing.extraLarge))

                    Text(
                        text = alarm.title,
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(AapsSpacing.extraLarge))

                    if (alarm.content.isNotBlank()) {
                        Text(
                            text = AnnotatedString.fromHtml(alarm.content.replace("\n", "<br>")),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(AapsSpacing.xxLarge))

                    Button(
                        onClick = onMute5MinClick,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = alarm.mute5minButtonText)
                    }

                    Spacer(modifier = Modifier.height(AapsSpacing.large))

                    Button(
                        onClick = onMuteClick,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = alarm.muteButtonText)
                    }

                    Spacer(modifier = Modifier.height(AapsSpacing.large))

                    Button(
                        onClick = onPrimaryClick,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = alarm.primaryButtonText)
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "Carelevo Alarm")
@Composable
private fun CarelevoAlarmScreenPreview() {
    MaterialTheme {
        CarelevoAlarmScreen(
            alarm = CarelevoAlarmUiModel(
                appIcon = R.drawable.ic_carelevo_128,
                title = "Low insulin warning",
                content = "Insulin remaining is below threshold.<br>Please confirm and replace soon.",
                primaryButtonText = "Confirm",
                muteButtonText = "Mute",
                mute5minButtonText = "Mute 5 min"
            ),
            onPrimaryClick = {},
            onMuteClick = {},
            onMute5MinClick = {}
        )
    }
}
