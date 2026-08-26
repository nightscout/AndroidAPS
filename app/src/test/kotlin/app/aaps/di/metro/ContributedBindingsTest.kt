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
        assertThat(root.sharedPreferences).isSameInstanceAs(root.sharedPreferences)
        assertThat(root.lastBgData).isSameInstanceAs(root.lastBgData)
        assertThat(root.localeDependentSetting).isSameInstanceAs(root.localeDependentSetting)
        assertThat(root.pumpStatusProvider).isSameInstanceAs(root.pumpStatusProvider)
        assertThat(root.passwordCheck).isSameInstanceAs(root.passwordCheck)
        assertThat(root.overviewData).isSameInstanceAs(root.overviewData)
        assertThat(root.exportPasswordDataStore).isSameInstanceAs(root.exportPasswordDataStore)
        assertThat(root.secureEncrypt).isSameInstanceAs(root.secureEncrypt)
        assertThat(root.cryptoUtil).isSameInstanceAs(root.cryptoUtil)
        assertThat(root.concentrationHelper).isSameInstanceAs(root.concentrationHelper)
        assertThat(root.processedTbrEbData).isSameInstanceAs(root.processedTbrEbData)
        assertThat(root.userEntryLogger).isSameInstanceAs(root.userEntryLogger)
        assertThat(root.glucoseStatusProvider).isSameInstanceAs(root.glucoseStatusProvider)
        assertThat(root.fileListProvider).isSameInstanceAs(root.fileListProvider)
        assertThat(root.maintenance).isSameInstanceAs(root.maintenance)
        assertThat(root.importExportPrefs).isSameInstanceAs(root.importExportPrefs)
        assertThat(root.preferences).isSameInstanceAs(root.preferences)
        assertThat(root.calculationWorkflow).isSameInstanceAs(root.calculationWorkflow)
        // Holds the chain generation counter, so a second copy would silently break the race guard.
        assertThat(root.workflowChainData).isSameInstanceAs(root.workflowChainData)
        // Moved off :shared:impl's Dagger modules, which :wear still uses. RxBus especially: a second
        // bus means events posted on one half are never seen by the other.
        assertThat(root.aapsLogger).isSameInstanceAs(root.aapsLogger)
        assertThat(root.rxBus).isSameInstanceAs(root.rxBus)
        assertThat(root.dateUtil).isSameInstanceAs(root.dateUtil)
        assertThat(root.l).isSameInstanceAs(root.l)
        assertThat(root.aapsSchedulers).isSameInstanceAs(root.aapsSchedulers)
        assertThat(root.sp).isSameInstanceAs(root.sp)
        assertThat(root.sceneIconResolver).isSameInstanceAs(root.sceneIconResolver)
        assertThat(root.processedDeviceStatusData).isSameInstanceAs(root.processedDeviceStatusData)
        assertThat(root.lastLocationDataContainer).isSameInstanceAs(root.lastLocationDataContainer)
        // Its init starts six channel consumers on the app scope; a second copy would consume the same
        // requests twice and write every incoming NS record to the database twice.
        assertThat(root.storeDataForDb).isSameInstanceAs(root.storeDataForDb)
        assertThat(root.sceneExecutor).isSameInstanceAs(root.sceneExecutor)
        // The inbox the broadcast receivers hand data to - two of them means dropped readings.
        assertThat(root.dataInbox).isSameInstanceAs(root.dataInbox)
        assertThat(root.activePlugin).isSameInstanceAs(root.activePlugin)
        assertThat(root.runningConfiguration).isSameInstanceAs(root.runningConfiguration)
        // One object bound to two interfaces, as the two @Binds were. It holds the config it read from
        // Nightscout, so a second copy would answer from an empty one.
        assertThat(root.runningConfigurationKeys).isSameInstanceAs(root.runningConfiguration)
    }

    @Test
    fun `ActivePlugin, PluginPermissions and PluginStore are one object`() {
        // PluginStore holds `plugins` as a lateinit var that MainApp assigns after the graph is built.
        // If these three resolved to different instances, MainApp would fill one and every
        // ActivePlugin lookup in the app would read an uninitialised lateinit - the loop would not
        // find its APS, its pump or its sensitivity plugin. Nothing about that fails to compile.
        val root = testRoot()
        assertThat(root.activePlugin).isSameInstanceAs(root.pluginStore)
        assertThat(root.pluginPermissions).isSameInstanceAs(root.pluginStore)
    }

    @Test
    fun `the unscoped bindings stay UNSCOPED, as they were under Dagger`() {
        // The @Binds they replaced had no @Singleton, so every injection site got its own. Scoping them
        // now would be a silent behaviour change - and for the two pump ones, in classes that talk to
        // the pump.
        val root = testRoot()
        assertThat(root.pumpSync).isNotSameInstanceAs(root.pumpSync)
        assertThat(root.pumpWithConcentration).isNotSameInstanceAs(root.pumpWithConcentration)
        assertThat(root.widgetUpdater).isNotSameInstanceAs(root.widgetUpdater)
    }
}
