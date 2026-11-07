package app.aaps.core.ui.compose.siteRotation

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Pins the body-diagram hit test, which decides which site a tap selects.
 *
 * `containsPoint` used to be built on `android.graphics.Region`; it is now a Compose `Path`
 * intersection so the code can leave Android. A hit test that is subtly wrong does not crash and
 * does not fail a build - it just selects the wrong infusion site, or none - so it is worth pinning
 * rather than eyeballing.
 *
 * Robolectric because `androidx.compose.ui.graphics.Path` needs a real graphics implementation.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class BodyViewContainsPointTest {

    private fun square(left: Float, top: Float, right: Float, bottom: Float) =
        Path().apply { addRect(Rect(left, top, right, bottom)) }

    @Test
    fun `a point well inside is a hit`() {
        val zone = square(10f, 10f, 20f, 20f)
        assertThat(zone.containsPoint(15f, 15f)).isTrue()
        assertThat(zone.containsPoint(11f, 11f)).isTrue()
        assertThat(zone.containsPoint(19f, 19f)).isTrue()
    }

    @Test
    fun `a point outside is not a hit`() {
        val zone = square(10f, 10f, 20f, 20f)
        assertThat(zone.containsPoint(5f, 15f)).isFalse()
        assertThat(zone.containsPoint(25f, 15f)).isFalse()
        assertThat(zone.containsPoint(15f, 5f)).isFalse()
        assertThat(zone.containsPoint(15f, 25f)).isFalse()
    }

    @Test
    fun `a point far away is not a hit`() {
        val zone = square(10f, 10f, 20f, 20f)
        assertThat(zone.containsPoint(0f, 0f)).isFalse()
        assertThat(zone.containsPoint(1000f, 1000f)).isFalse()
    }

    @Test
    fun `neighbouring zones do not both claim the same tap`() {
        // The body diagram is a list of adjacent zones and the first hit wins, so two zones
        // answering true for one tap would make the selection depend on list order.
        val left = square(0f, 0f, 10f, 10f)
        val right = square(20f, 0f, 30f, 10f)
        assertThat(left.containsPoint(5f, 5f)).isTrue()
        assertThat(right.containsPoint(5f, 5f)).isFalse()
        assertThat(left.containsPoint(25f, 5f)).isFalse()
        assertThat(right.containsPoint(25f, 5f)).isTrue()
    }

    @Test
    fun `an empty path never claims a tap`() {
        assertThat(Path().containsPoint(0f, 0f)).isFalse()
    }
}
