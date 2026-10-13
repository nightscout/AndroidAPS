package app.aaps.ui.compose.overview.graphs

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Which point of a predicted curve is spoken, and in which direction.
 *
 * The predicted curves are drawn and nothing on the screen states them, so a blind user could not
 * reach them at all. Each curve is read as one sentence, and this picks the number in it. Getting
 * the direction wrong would tell someone their glucose is heading the opposite way, so the awkward
 * shapes are pinned down here rather than left to the composable.
 */
class PredictionExtremeTest {

    @Test
    fun `an empty curve says nothing`() {
        assertThat(predictionExtreme(emptyList(), current = 6.0)).isNull()
    }

    @Test
    fun `a rising curve reports its peak`() {
        val (value, direction) = predictionExtreme(listOf(6.2, 7.4, 9.1, 11.2), current = 6.0)!!
        assertThat(value).isEqualTo(11.2)
        assertThat(direction).isEqualTo(PredictionDirection.UP)
    }

    @Test
    fun `a falling curve reports its trough`() {
        val (value, direction) = predictionExtreme(listOf(5.6, 4.8, 4.1, 3.9), current = 6.0)!!
        assertThat(value).isEqualTo(3.9)
        assertThat(direction).isEqualTo(PredictionDirection.DOWN)
    }

    @Test
    fun `a curve that dips before recovering reports the dip, not the last point`() {
        // The whole reason for taking the furthest excursion: ending back at 6.0 is not the news,
        // the 3.2 on the way is. Reporting the final point would have called this one flat.
        val (value, direction) = predictionExtreme(listOf(5.2, 4.0, 3.2, 4.4, 6.0), current = 6.0)!!
        assertThat(value).isEqualTo(3.2)
        assertThat(direction).isEqualTo(PredictionDirection.DOWN)
    }

    @Test
    fun `a curve that rises further than it dips reports the rise`() {
        val (value, direction) = predictionExtreme(listOf(5.8, 7.0, 12.0, 9.0), current = 6.0)!!
        assertThat(value).isEqualTo(12.0)
        assertThat(direction).isEqualTo(PredictionDirection.UP)
    }

    @Test
    fun `a level curve holds its value`() {
        val (value, direction) = predictionExtreme(listOf(6.0, 6.0, 6.0), current = 6.0)!!
        assertThat(value).isEqualTo(6.0)
        assertThat(direction).isEqualTo(PredictionDirection.FLAT)
    }

    @Test
    fun `an equal excursion either way is called level rather than guessed`() {
        // Up 2.0 and down 2.0: there is no honest direction to name, so it reports the current value.
        val (value, direction) = predictionExtreme(listOf(8.0, 4.0), current = 6.0)!!
        assertThat(value).isEqualTo(6.0)
        assertThat(direction).isEqualTo(PredictionDirection.FLAT)
    }

    @Test
    fun `a curve entirely above the current value still reports its peak`() {
        // Every point is higher, so the "down" excursion is negative and must not win.
        val (value, direction) = predictionExtreme(listOf(7.0, 8.0, 9.0), current = 6.0)!!
        assertThat(value).isEqualTo(9.0)
        assertThat(direction).isEqualTo(PredictionDirection.UP)
    }

    @Test
    fun `a curve entirely below the current value still reports its trough`() {
        val (value, direction) = predictionExtreme(listOf(5.0, 4.0, 3.0), current = 6.0)!!
        assertThat(value).isEqualTo(3.0)
        assertThat(direction).isEqualTo(PredictionDirection.DOWN)
    }
}
