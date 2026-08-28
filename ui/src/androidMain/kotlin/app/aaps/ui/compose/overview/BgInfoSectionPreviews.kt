package app.aaps.ui.compose.overview

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.interfaces.overview.graph.BgInfoData
import app.aaps.core.interfaces.overview.graph.BgRange

// A fixed instant: a preview must render the same every time, and there is no clock provided to it.
private const val PREVIEW_NOW = 1_780_000_000_000L

@Preview(showBackground = true)
@Composable
internal fun BgInfoSectionInRangePreview() {
    MaterialTheme {
        BgInfoSection(
            bgInfo = BgInfoData(
                bgValue = 120.0,
                bgText = "120",
                bgRange = BgRange.IN_RANGE,
                isOutdated = false,
                timestamp = PREVIEW_NOW,
                trendArrow = TrendArrow.FLAT,
                trendDescription = "Flat",
                delta = 2.0,
                deltaText = "+2",
                shortAvgDelta = 1.5,
                shortAvgDeltaText = "+1.5",
                longAvgDelta = 1.0,
                longAvgDeltaText = "+1.0"
            ),
            timeAgoText = "2 min"
        )
    }
}

@Preview(showBackground = true)
@Composable
internal fun BgInfoSectionHighPreview() {
    MaterialTheme {
        BgInfoSection(
            bgInfo = BgInfoData(
                bgValue = 220.0,
                bgText = "220",
                bgRange = BgRange.HIGH,
                isOutdated = false,
                timestamp = PREVIEW_NOW,
                trendArrow = TrendArrow.DOUBLE_UP,
                trendDescription = "Rising fast",
                delta = 15.0,
                deltaText = "+15",
                shortAvgDelta = 12.0,
                shortAvgDeltaText = "+12",
                longAvgDelta = 10.0,
                longAvgDeltaText = "+10"
            ),
            timeAgoText = "1 min"
        )
    }
}

@Preview(showBackground = true)
@Composable
internal fun BgInfoSectionLowPreview() {
    MaterialTheme {
        BgInfoSection(
            bgInfo = BgInfoData(
                bgValue = 65.0,
                bgText = "65",
                bgRange = BgRange.LOW,
                isOutdated = false,
                timestamp = PREVIEW_NOW,
                trendArrow = TrendArrow.TRIPLE_DOWN,
                trendDescription = "Falling rapidly",
                delta = -10.0,
                deltaText = "-10",
                shortAvgDelta = -8.0,
                shortAvgDeltaText = "-8",
                longAvgDelta = -6.0,
                longAvgDeltaText = "-6"
            ),
            timeAgoText = "3 min"
        )
    }
}

@Preview(showBackground = true)
@Composable
internal fun BgInfoSectionNullPreview() {
    MaterialTheme {
        BgInfoSection(
            bgInfo = null,
            timeAgoText = ""
        )
    }
}
