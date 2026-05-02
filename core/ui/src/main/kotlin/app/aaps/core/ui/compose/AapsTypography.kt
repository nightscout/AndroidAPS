package app.aaps.core.ui.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.isSpecified
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
 *
 * @param scale Multiplier applied to all font sizes. 1.0 for phones, ~1.25 for tablets.
 */
@Composable
fun aapsTypography(scale: Float = 1f): AapsTypography {
    val mt = MaterialTheme.typography
    return remember(mt, scale) {
        AapsTypography(
            bgValue = mt.displayMedium.copy(fontSize = (50 * scale).sp, fontWeight = FontWeight.Bold, lineHeight = (52 * scale).sp),
            bgSecondary = mt.bodySmall.copy(fontSize = (17 * scale).sp, fontWeight = FontWeight.Bold, lineHeight = (19 * scale).sp),
            bgTimeAgo = mt.bodySmall.copy(fontSize = (17 * scale).sp, lineHeight = (19 * scale).sp),
            treatmentValue = mt.bodyMedium.copy(fontSize = (14 * scale).sp),
            treatmentDetail = mt.bodySmall.copy(fontSize = (12 * scale).sp),
            chipLabel = mt.bodySmall.copy(fontSize = (12 * scale).sp),
            sectionHeader = mt.titleMedium.copy(fontSize = (16 * scale).sp, fontWeight = FontWeight.SemiBold),
        )
    }
}

/**
 * Returns a [Typography] with every font size and line height multiplied by [scale].
 * Used to scale Material 3 typography uniformly on tablets while keeping defaults on phones.
 */
fun Typography.scaled(scale: Float): Typography {
    if (scale == 1f) return this
    fun TextStyle.s() = copy(
        fontSize = if (fontSize.isSpecified) fontSize * scale else fontSize,
        lineHeight = if (lineHeight.isSpecified) lineHeight * scale else lineHeight,
    )
    return copy(
        displayLarge = displayLarge.s(),
        displayMedium = displayMedium.s(),
        displaySmall = displaySmall.s(),
        headlineLarge = headlineLarge.s(),
        headlineMedium = headlineMedium.s(),
        headlineSmall = headlineSmall.s(),
        titleLarge = titleLarge.s(),
        titleMedium = titleMedium.s(),
        titleSmall = titleSmall.s(),
        bodyLarge = bodyLarge.s(),
        bodyMedium = bodyMedium.s(),
        bodySmall = bodySmall.s(),
        labelLarge = labelLarge.s(),
        labelMedium = labelMedium.s(),
        labelSmall = labelSmall.s(),
    )
}
