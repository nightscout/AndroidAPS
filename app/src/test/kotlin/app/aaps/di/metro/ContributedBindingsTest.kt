package app.aaps.di.metro

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
        assertThat(root.translator).isSameInstanceAs(root.translator)
        assertThat(root.protectionCheck).isSameInstanceAs(root.protectionCheck)
        assertThat(root.tddCalculator).isSameInstanceAs(root.tddCalculator)
        assertThat(root.tirCalculator).isSameInstanceAs(root.tirCalculator)
        assertThat(root.dexcomTirCalculator).isSameInstanceAs(root.dexcomTirCalculator)
        assertThat(root.iconsProvider).isSameInstanceAs(root.iconsProvider)
        assertThat(root.insulinManager).isSameInstanceAs(root.insulinManager)
        assertThat(root.wizardBolusExecutor).isSameInstanceAs(root.wizardBolusExecutor)
        assertThat(root.loggerUtils).isSameInstanceAs(root.loggerUtils)
        assertThat(root.alarmSoundPlayer).isSameInstanceAs(root.alarmSoundPlayer)
        assertThat(root.notificationHolder).isSameInstanceAs(root.notificationHolder)
        assertThat(root.userEntryPresentationHelper).isSameInstanceAs(root.userEntryPresentationHelper)
        assertThat(root.profiler).isSameInstanceAs(root.profiler)
        assertThat(root.concentrationHelper).isSameInstanceAs(root.concentrationHelper)
        assertThat(root.processedTbrEbData).isSameInstanceAs(root.processedTbrEbData)
        assertThat(root.userEntryLogger).isSameInstanceAs(root.userEntryLogger)
        assertThat(root.glucoseStatusProvider).isSameInstanceAs(root.glucoseStatusProvider)
    }

    @Test
    fun `PumpSync stays UNSCOPED, as it was under Dagger`() {
        // The @Binds it replaced had no @Singleton, so every injection site got its own. Scoping it now
        // would be a silent behaviour change in a class that talks to the pump.
        val root = testRoot()
        assertThat(root.pumpSync).isNotSameInstanceAs(root.pumpSync)
    }
}
