package app.aaps.core.ui.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Custom typography scale for AndroidAPS Compose UI.
 *
 * Supplements Material 3 typography with domain-specific text styles
 * used across the app (BG display, treatments, chips, section headers).
 *
 * **Usage:**
 * ```kotlin
 * Text(
 *     text = bgValue,
 *     style = AapsTheme.typography.bgValue
 * )
 * ```
 */
@Immutable
data class AapsTypography(
    /** 50sp Bold — primary BG reading in the overview circle */
    val bgValue: TextStyle,
    /** 17sp Bold — delta text near the BG circle */
    val bgSecondary: TextStyle,
    /** 17sp — time-ago text near the BG circle */
    val bgTimeAgo: TextStyle,
    /** 14sp — data values in treatment list items */
    val treatmentValue: TextStyle,
    /** 12sp — secondary details in treatment list items */
    val treatmentDetail: TextStyle,
    /** 12sp — chip label text (sensitivity, IOB, etc.) */
    val chipLabel: TextStyle,
    /** titleMedium + SemiBold — section titles / headers */
    val sectionHeader: TextStyle,
)

/**
 * CompositionLocal for AapsTypography.
 * Default instance uses hardcoded values so previews work without AapsTheme wrapper.
 */
val LocalAapsTypography = compositionLocalOf {
    AapsTypography(
        bgValue = TextStyle(fontSize = 50.sp, fontWeight = FontWeight.Bold, lineHeight = 52.sp),
        bgSecondary = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, lineHeight = 19.sp),
        bgTimeAgo = TextStyle(fontSize = 17.sp, lineHeight = 19.sp),
        treatmentValue = TextStyle(fontSize = 14.sp),
        treatmentDetail = TextStyle(fontSize = 12.sp),
        chipLabel = TextStyle(fontSize = 12.sp),
        sectionHeader = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    )
}

/**
 * Builds [AapsTypography] from [MaterialTheme.typography] base styles.
 * Must be called inside a [MaterialTheme] scope.
 */
@Composable
fun aapsTypography(): AapsTypography {
    val mt = MaterialTheme.typography
    return AapsTypography(
        bgValue = mt.displayMedium.copy(fontSize = 50.sp, fontWeight = FontWeight.Bold, lineHeight = 52.sp),
        bgSecondary = mt.bodySmall.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold, lineHeight = 19.sp),
        bgTimeAgo = mt.bodySmall.copy(fontSize = 17.sp, lineHeight = 19.sp),
        treatmentValue = mt.bodyMedium.copy(fontSize = 14.sp),
        treatmentDetail = mt.bodySmall.copy(fontSize = 12.sp),
        chipLabel = mt.bodySmall.copy(fontSize = 12.sp),
        sectionHeader = mt.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    )
}
