package app.aaps.ui.compose.overview

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.interfaces.alerts.EatReminderState
import kotlin.time.Clock

// IDE-only previews for EatReminderBanner, in their own *Previews.kt file so JaCoCo leaves them out
// (see ActiveSceneBannerPreviews for why).

@Preview(showBackground = true)
@Composable
internal fun EatReminderBannerRunningPreview() {
    MaterialTheme {
        Surface {
            // The banner reads the clock itself, so a running timer has to be built around the real time.
            val now = Clock.System.now().toEpochMilliseconds()
            EatReminderBannerContent(
                state = EatReminderState(startedAt = now - 8 * 60_000L, endsAt = now + 12 * 60_000L + 5_000L, durationMs = 20 * 60_000L),
                onStop = {},
                onExtend = {}
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
internal fun EatReminderBannerRungPreview() {
    MaterialTheme {
        Surface {
            EatReminderBannerContent(
                state = EatReminderState(startedAt = 0L, endsAt = 20 * 60_000L, durationMs = 20 * 60_000L),
                onStop = {},
                onExtend = {}
            )
        }
    }
}
