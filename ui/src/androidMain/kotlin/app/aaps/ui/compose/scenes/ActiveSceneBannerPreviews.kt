package app.aaps.ui.compose.scenes

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.data.model.ActiveSceneState
import app.aaps.core.data.model.Scene

// IDE-only previews for ActiveSceneBanner. Kept in a dedicated *Previews.kt file so the whole class
// (including the synthetic $lambda$N methods the Compose compiler extracts from each @Composable
// lambda) is excluded from JaCoCo coverage via the `**/*PreviewsKt*.class` glob — a method-level
// annotation cannot reach those extracted lambdas. See jacoco_aggregation.gradle.kts.

private fun sampleScene(name: String = "Exercise") = Scene(
    id = "preview",
    name = name,
    defaultDurationMinutes = 60
)

@Preview(showBackground = true)
@Composable
internal fun ActiveSceneBannerTimedPreview() {
    val now = System.currentTimeMillis()
    MaterialTheme {
        Surface {
            ActiveSceneBannerContent(
                state = ActiveSceneState(
                    scene = sampleScene(),
                    activatedAt = now - 30 * 60_000L, // started 30 min ago
                    durationMs = 60 * 60_000L,        // 60 min total
                    scopedRecords = ActiveSceneState.ScopedRecords()
                ),
                onEndClick = {}
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
internal fun ActiveSceneBannerExpiredPreview() {
    val now = System.currentTimeMillis()
    MaterialTheme {
        Surface {
            ActiveSceneBannerContent(
                state = ActiveSceneState(
                    scene = sampleScene(),
                    activatedAt = now - 60 * 60_000L,
                    durationMs = 60 * 60_000L,
                    scopedRecords = ActiveSceneState.ScopedRecords()
                ),
                expired = true,
                onEndClick = {},
                onDismiss = {}
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
internal fun ActiveSceneBannerIndefinitePreview() {
    val now = System.currentTimeMillis()
    MaterialTheme {
        Surface {
            ActiveSceneBannerContent(
                state = ActiveSceneState(
                    scene = sampleScene("Sick Day"),
                    activatedAt = now - 120 * 60_000L,
                    durationMs = 0, // indefinite
                    scopedRecords = ActiveSceneState.ScopedRecords()
                ),
                onEndClick = {}
            )
        }
    }
}
