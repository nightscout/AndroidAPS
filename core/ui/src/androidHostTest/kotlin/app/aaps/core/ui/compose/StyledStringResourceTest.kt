package app.aaps.core.ui.compose

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * A translation can put the values of a styled text in another order. The style must stay with its
 * value, not with its position.
 */
class StyledStringResourceTest {

    private val bold = SpanStyle(fontWeight = FontWeight.Bold)

    @Test
    fun `each value is placed and styled`() {
        val text = styleArguments("Carbs: 0 at 1", listOf(StyledArg("20 g", bold), StyledArg("12:30")))
        assertThat(text.text).isEqualTo("Carbs: 20 g at 12:30")
        assertThat(text.spanStyles).hasSize(1)
        assertThat(text.spanStyles[0].item).isEqualTo(bold)
        assertThat(text.text.substring(text.spanStyles[0].start, text.spanStyles[0].end)).isEqualTo("20 g")
    }

    @Test
    fun `the style follows its value when the order changes`() {
        val text = styleArguments("1 - 0", listOf(StyledArg("20 g", bold), StyledArg("12:30")))
        assertThat(text.text).isEqualTo("12:30 - 20 g")
        assertThat(text.text.substring(text.spanStyles[0].start, text.spanStyles[0].end)).isEqualTo("20 g")
    }

    @Test
    fun `a text without values stays as it is`() {
        assertThat(styleArguments("Carbs", emptyList()).text).isEqualTo("Carbs")
    }
}
