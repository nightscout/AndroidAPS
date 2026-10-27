package app.aaps.wear.eatreminder

import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import app.aaps.core.interfaces.alerts.EatReminder
import app.aaps.wear.R
import app.aaps.wear.di.WearMetroActivity
import app.aaps.wear.interaction.actions.CarbsOrange
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.delay

/**
 * The "time to eat" timer rang: the watch vibrates and offers Silence and +5 min. Both go to the phone
 * through [EatReminderWatch]. The screen is closed by it when the phone ends or moves the timer.
 *
 * Opened from a broadcast receiver, which Android may refuse from the background; the notification
 * [EatReminderWatch] posts at the same time has the same two buttons, so nothing is lost then.
 */
class EatReminderActivity : WearMetroActivity() {

    @Inject lateinit var eatReminderWatch: EatReminderWatch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        current = this
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        val vibrator = getSystemService(Vibrator::class.java)
        vibrator?.vibrate(VibrationEffect.createWaveform(RING_PATTERN, 0))
        setContent {
            MaterialTheme {
                // The screen stays until the user acts, the vibration does not
                LaunchedEffect(Unit) {
                    delay(VIBRATE_MS)
                    vibrator?.cancel()
                }
                EatReminderScreen(
                    onSilence = { eatReminderWatch.silence() },
                    onExtend = { eatReminderWatch.extend() }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        getSystemService(Vibrator::class.java)?.cancel()
        if (current === this) current = null
    }

    companion object {

        private val RING_PATTERN = longArrayOf(0, 500, 300, 500, 1500)
        private const val VIBRATE_MS = 60_000L

        // One screen at a time, so the phone's answer can close it
        @Volatile private var current: EatReminderActivity? = null

        fun close() {
            current?.finish()
        }
    }
}

@Composable
private fun EatReminderScreen(onSilence: () -> Unit, onExtend: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_alarm),
            contentDescription = null,
            tint = CarbsOrange,
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.eat_reminder_title),
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onSilence,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.15f), contentColor = Color.White),
            label = { Text(stringResource(R.string.eat_reminder_silence)) }
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onExtend,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = CarbsOrange, contentColor = Color.Black),
            label = { Text(stringResource(R.string.eat_reminder_extend, EatReminder.EXTEND_MINUTES)) }
        )
    }
}
