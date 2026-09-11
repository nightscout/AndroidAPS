package app.aaps.di.metro

import app.aaps.implementation.utils.TrendCalculatorImpl
import app.aaps.core.interfaces.plugin.PermissionGroup
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.keys.interfaces.TextRef
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Every `@ContributesBinding` implementation reaches the graph, and is scoped.
 *
 * Neither of those fails the build if it stops being true:
 *
 *  - a binding that does not reach the root graph means nothing can depend on it;
 *  - an unscoped binding gives **every caller its own copy**, which for a class holding state shows up
 *    only as two parts of the app disagreeing. `RxBus` is the sharpest example: a second bus means
 *    events posted on one side are never seen by the other. `ProfileSwitchSilentGate` is the one that
 *    actually shipped - the flag was marked on one instance and read on another, so a scene profile
 *    switch raised the notification the gate exists to suppress.
 */
class ContributedBindingsTest {

    @Test
    fun `the trend calculator is contributed to the root graph`() {
        assertThat(testRoot().trendCalculator).isInstanceOf(TrendCalculatorImpl::class.java)
    }

    @Test
    fun `every contributed implementation is scoped`() {
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
        assertThat(root.profileSwitchSilentGate).isSameInstanceAs(root.profileSwitchSilentGate)
        // Holds the queue of pending pump commands and the one being performed. A second copy would
        // accept commands that the copy the pump driver reads never sees.
        assertThat(root.commandQueue).isSameInstanceAs(root.commandQueue)
        assertThat(root.localAlertUtils).isSameInstanceAs(root.localAlertUtils)
        assertThat(root.bolusProgressData).isSameInstanceAs(root.bolusProgressData)
        assertThat(root.persistenceLayer).isSameInstanceAs(root.persistenceLayer)
        assertThat(root.cloudStorageManager).isSameInstanceAs(root.cloudStorageManager)
        assertThat(root.overviewDataCache).isSameInstanceAs(root.overviewDataCache)
        assertThat(root.calculationSignals).isSameInstanceAs(root.calculationSignals)
        assertThat(root.calculationSignalsEmitter).isSameInstanceAs(root.calculationSignalsEmitter)
        assertThat(root.calculationSignals).isSameInstanceAs(root.calculationSignalsEmitter)
        // A multibinding hands out a fresh Set each read; what has to be shared is the provider in it.
        assertThat(root.cloudStorageProviders.single()).isSameInstanceAs(root.cloudStorageProviders.single())
        assertThat(root.constraintsChecker).isSameInstanceAs(root.constraintsChecker)
        assertThat(root.nsClientRepository).isSameInstanceAs(root.nsClientRepository)
    }

    @Test
    fun `ActivePlugin and PluginStore are one object`() {
        // PluginStore holds `plugins` as a lateinit var that MainApp assigns after the graph is built.
        // If these resolved to different instances, MainApp would fill one and every ActivePlugin
        // lookup in the app would read an uninitialised lateinit - the loop would not find its APS,
        // its pump or its sensitivity plugin. Nothing about that fails to compile.
        val root = testRoot()
        assertThat(root.activePlugin).isSameInstanceAs(root.pluginStore)
    }

    @Test
    fun `PluginPermissions reads the same plugin list MainApp filled in`() {
        // PluginPermissions is deliberately a separate object now: it is the only part of the old
        // PluginStore that needed Android. It still has to see the plugins MainApp assigns, which it
        // does by holding the same ActivePlugin. Asserting the list rather than the identity says
        // what actually matters, and keeps saying it if the wiring changes shape again.
        val root = testRoot()
        val plugin = mock<PluginBase>()
        whenever(plugin.isEnabled()).thenReturn(true)
        whenever(plugin.requiredPermissions()).thenReturn(listOf(probeGroup))
        root.pluginStore.plugins = listOf(plugin)

        assertThat(root.pluginPermissions.collectAllPermissions()).contains(probeGroup)
    }

    private val probeGroup = PermissionGroup(
        permissions = listOf("app.aaps.permission.PROBE"),
        rationaleTitle = TextRef.Literal(""),
        rationaleDescription = TextRef.Literal("")
    )

    @Test
    fun `the unscoped bindings stay UNSCOPED`() {
        // The @Binds they replaced had no @Singleton, so every injection site got its own. Scoping them
        // now would be a silent behaviour change - and for the two pump ones, in classes that talk to
        // the pump.
        val root = testRoot()
        assertThat(root.pumpSync).isNotSameInstanceAs(root.pumpSync)
        assertThat(root.pumpWithConcentration).isNotSameInstanceAs(root.pumpWithConcentration)
        assertThat(root.widgetUpdater).isNotSameInstanceAs(root.widgetUpdater)
        // Result objects: a fresh one per call is the point.
        assertThat(root.apsResult).isNotSameInstanceAs(root.apsResult)
        assertThat(root.pumpEnactResult).isNotSameInstanceAs(root.pumpEnactResult)
    }
}
