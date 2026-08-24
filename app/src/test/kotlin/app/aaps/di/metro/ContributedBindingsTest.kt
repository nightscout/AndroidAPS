package app.aaps.di.metro

import app.aaps.core.interfaces.utils.TrendCalculator
import app.aaps.implementation.utils.TrendCalculatorImpl
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Implementations that moved from a Dagger `@Binds` to a Metro `@ContributesBinding`.
 *
 * Two things have to hold for each one, and neither fails the build:
 *
 *  - the binding reaches the root graph at all, so anything Metro builds can depend on it;
 *  - it is scoped, so the `@Provides` delegate in `CoreObjectsModule` hands Dagger consumers the same
 *    object the Metro side uses. An unscoped binding gives every caller its own, which for a class
 *    holding state is a bug that only shows up as two halves of the app disagreeing.
 */
class ContributedBindingsTest {

    @Test
    fun `the trend calculator is contributed to the root graph`() {
        assertThat(testRoot().trendCalculator).isInstanceOf(TrendCalculatorImpl::class.java)
    }

    @Test
    fun `every contributed implementation is scoped`() {
        // Same property as the plugins: an unscoped binding means the @Provides delegate hands Dagger a
        // different object than the Metro side holds, and for a class with state the two halves of the
        // app then disagree. Nothing about that fails the build.
        val root = testRoot()
        assertThat(root.trendCalculator).isSameInstanceAs(root.trendCalculator)
        assertThat(root.decimalFormatter).isSameInstanceAs(root.decimalFormatter)
        assertThat(root.profileUtil).isSameInstanceAs(root.profileUtil)
        assertThat(root.hardLimits).isSameInstanceAs(root.hardLimits)
        assertThat(root.storage).isSameInstanceAs(root.storage)
        assertThat(root.receiverStatusStore).isSameInstanceAs(root.receiverStatusStore)
    }
}
