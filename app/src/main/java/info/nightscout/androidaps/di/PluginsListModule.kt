package info.nightscout.androidaps.di

import dagger.Binds
import dagger.Module
import dagger.multibindings.IntKey
import dagger.multibindings.IntoMap
import info.nightscout.androidaps.danaRKorean.DanaRKoreanPlugin
import info.nightscout.androidaps.danaRv2.DanaRv2Plugin
import info.nightscout.androidaps.danar.DanaRPlugin
import info.nightscout.plugins.sync.openhumans.OpenHumansUploaderPlugin
import info.nightscout.androidaps.plugins.pump.eopatch.EopatchPumpPlugin
import info.nightscout.androidaps.plugins.pump.insight.LocalInsightPlugin
import info.nightscout.androidaps.plugins.pump.medtronic.MedtronicPumpPlugin
import info.nightscout.androidaps.plugins.pump.omnipod.dash.OmnipodDashPumpPlugin
import info.nightscout.androidaps.plugins.pump.omnipod.eros.OmnipodErosPumpPlugin
import info.nightscout.automation.AutomationPlugin
import info.nightscout.configuration.configBuilder.ConfigBuilderPlugin
import info.nightscout.configuration.maintenance.MaintenancePlugin
import info.nightscout.insulin.InsulinLyumjevPlugin
import info.nightscout.insulin.InsulinOrefFreePeakPlugin
import info.nightscout.insulin.InsulinOrefRapidActingPlugin
import info.nightscout.insulin.InsulinOrefUltraRapidActingPlugin
import info.nightscout.interfaces.plugin.PluginBase
import info.nightscout.plugins.aps.loop.LoopPlugin
import info.nightscout.plugins.aps.openAPSAMA.OpenAPSAMAPlugin
import info.nightscout.plugins.aps.openAPSSMB.OpenAPSSMBPlugin
import info.nightscout.plugins.aps.openAPSSMBDynamicISF.OpenAPSSMBDynamicISFPlugin
import info.nightscout.plugins.constraints.bgQualityCheck.BgQualityCheckPlugin
import info.nightscout.plugins.constraints.objectives.ObjectivesPlugin
import info.nightscout.plugins.constraints.safety.SafetyPlugin
import info.nightscout.plugins.constraints.signatureVerifier.SignatureVerifierPlugin
import info.nightscout.plugins.general.actions.ActionsPlugin
import info.nightscout.plugins.general.autotune.AutotunePlugin
import info.nightscout.plugins.general.food.FoodPlugin
import info.nightscout.plugins.general.overview.OverviewPlugin
import info.nightscout.plugins.general.persistentNotification.PersistentNotificationPlugin
import info.nightscout.plugins.general.smsCommunicator.SmsCommunicatorPlugin
import info.nightscout.plugins.general.themes.ThemeSwitcherPlugin
import info.nightscout.plugins.general.wear.WearPlugin
import info.nightscout.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin
import info.nightscout.plugins.profile.ProfilePlugin
import info.nightscout.plugins.sync.dataBroadcaster.DataBroadcastPlugin
import info.nightscout.plugins.sync.nsclient.NSClientPlugin
import info.nightscout.plugins.sync.nsclientV3.NSClientV3Plugin
import info.nightscout.plugins.sync.tidepool.TidepoolPlugin
import info.nightscout.plugins.sync.xdrip.XdripPlugin
import info.nightscout.pump.combo.ComboPlugin
import info.nightscout.pump.combov2.ComboV2Plugin
import info.nightscout.pump.diaconn.DiaconnG8Plugin
import info.nightscout.pump.virtual.VirtualPumpPlugin
import info.nightscout.sensitivity.SensitivityAAPSPlugin
import info.nightscout.sensitivity.SensitivityOref1Plugin
import info.nightscout.sensitivity.SensitivityWeightedAveragePlugin
import info.nightscout.smoothing.AvgSmoothingPlugin
import info.nightscout.smoothing.ExponentialSmoothingPlugin
import info.nightscout.smoothing.NoSmoothingPlugin
import info.nightscout.source.AidexPlugin
import info.nightscout.source.DexcomPlugin
import info.nightscout.source.GlimpPlugin
import info.nightscout.source.GlunovoPlugin
import info.nightscout.source.IntelligoPlugin
import info.nightscout.source.MM640gPlugin
import info.nightscout.source.NSClientSourcePlugin
import info.nightscout.source.PoctechPlugin
import info.nightscout.source.RandomBgPlugin
import info.nightscout.source.TomatoPlugin
import info.nightscout.source.XdripSourcePlugin
import javax.inject.Qualifier

@Suppress("unused")
@Module
abstract class PluginsListModule {

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(0)
    abstract fun bindPersistentNotificationPlugin(plugin: PersistentNotificationPlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(5)
    abstract fun bindOverviewPlugin(plugin: OverviewPlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(10)
    abstract fun bindIobCobCalculatorPlugin(plugin: IobCobCalculatorPlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(20)
    abstract fun bindActionsPlugin(plugin: ActionsPlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(30)
    abstract fun bindInsulinOrefRapidActingPlugin(plugin: InsulinOrefRapidActingPlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(40)
    abstract fun bindInsulinOrefUltraRapidActingPlugin(plugin: InsulinOrefUltraRapidActingPlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(42)
    abstract fun bindInsulinLyumjevPlugin(plugin: InsulinLyumjevPlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(50)
    abstract fun bindInsulinOrefFreePeakPlugin(plugin: InsulinOrefFreePeakPlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(60)
    abstract fun bindSensitivityAAPSPlugin(plugin: SensitivityAAPSPlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(70)
    abstract fun bindSensitivityWeightedAveragePlugin(plugin: SensitivityWeightedAveragePlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(80)
    abstract fun bindSensitivityOref1Plugin(plugin: SensitivityOref1Plugin): PluginBase

    @Binds
    @PumpDriver
    @IntoMap
    @IntKey(90)
    abstract fun bindDanaRPlugin(plugin: DanaRPlugin): PluginBase

    @Binds
    @PumpDriver
    @IntoMap
    @IntKey(100)
    abstract fun bindDanaRKoreanPlugin(plugin: DanaRKoreanPlugin): PluginBase

    @Binds
    @PumpDriver
    @IntoMap
    @IntKey(110)
    abstract fun bindDanaRv2Plugin(plugin: DanaRv2Plugin): PluginBase

    @Binds
    @PumpDriver
    @IntoMap
    @IntKey(120)
    abstract fun bindDanaRSPlugin(plugin: info.nightscout.pump.danars.DanaRSPlugin): PluginBase

    @Binds
    @PumpDriver
    @IntoMap
    @IntKey(130)
    abstract fun bindLocalInsightPlugin(plugin: LocalInsightPlugin): PluginBase

    @Binds
    @PumpDriver
    @IntoMap
    @IntKey(140)
    abstract fun bindComboPlugin(plugin: ComboPlugin): PluginBase

    @Binds
    @PumpDriver
    @IntoMap
    @IntKey(141)
    abstract fun bindComboV2Plugin(plugin: ComboV2Plugin): PluginBase

    @Binds
    @PumpDriver
    @IntoMap
    @IntKey(145)
    abstract fun bindOmnipodErosPumpPlugin(plugin: OmnipodErosPumpPlugin): PluginBase

    @Binds
    @PumpDriver
    @IntoMap
    @IntKey(148)
    abstract fun bindOmnipodDashPumpPlugin(plugin: OmnipodDashPumpPlugin): PluginBase

    @Binds
    @PumpDriver
    @IntoMap
    @IntKey(150)
    abstract fun bindMedtronicPumpPlugin(plugin: MedtronicPumpPlugin): PluginBase

    @Binds
    @PumpDriver
    @IntoMap
    @IntKey(155)
    abstract fun bindDiaconnG8Plugin(plugin: DiaconnG8Plugin): PluginBase

    @Binds
    @PumpDriver
    @IntoMap
    @IntKey(156)
    abstract fun bindEopatchPumpPlugin(plugin: EopatchPumpPlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(170)
    abstract fun bindVirtualPumpPlugin(plugin: VirtualPumpPlugin): PluginBase

    @Binds
    @APS
    @IntoMap
    @IntKey(190)
    abstract fun bindLoopPlugin(plugin: LoopPlugin): PluginBase

    @Binds
    @APS
    @IntoMap
    @IntKey(210)
    abstract fun bindOpenAPSAMAPlugin(plugin: OpenAPSAMAPlugin): PluginBase

    @Binds
    @APS
    @IntoMap
    @IntKey(220)
    abstract fun bindOpenAPSSMBPlugin(plugin: OpenAPSSMBPlugin): PluginBase

    @Binds
    @APS
    @IntoMap
    @IntKey(222)
    abstract fun bindOpenAPSSMBAutoISFPlugin(plugin: OpenAPSSMBDynamicISFPlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(240)
    abstract fun bindLocalProfilePlugin(plugin: ProfilePlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(250)
    abstract fun bindAutomationPlugin(plugin: AutomationPlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(255)
    abstract fun bindAutotunePlugin(plugin: AutotunePlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(265)
    abstract fun bindSafetyPlugin(plugin: SafetyPlugin): PluginBase

    @Binds
    @NotNSClient
    @IntoMap
    @IntKey(270)
    abstract fun bindVersionCheckerPlugin(plugin: info.nightscout.plugins.constraints.versionChecker.VersionCheckerPlugin): PluginBase

    @Binds
    @NotNSClient
    @IntoMap
    @IntKey(280)
    abstract fun bindSmsCommunicatorPlugin(plugin: SmsCommunicatorPlugin): PluginBase

    @Binds
    @APS
    @IntoMap
    @IntKey(290)
    abstract fun bindStorageConstraintPlugin(plugin: info.nightscout.plugins.constraints.storage.StorageConstraintPlugin): PluginBase

    @Binds
    @APS
    @IntoMap
    @IntKey(300)
    abstract fun bindSignatureVerifierPlugin(plugin: SignatureVerifierPlugin): PluginBase

    @Binds
    @APS
    @IntoMap
    @IntKey(310)
    abstract fun bindObjectivesPlugin(plugin: ObjectivesPlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(320)
    abstract fun bindFoodPlugin(plugin: FoodPlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(330)
    abstract fun bindWearPlugin(plugin: WearPlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(350)
    abstract fun bindNSClientPlugin(plugin: NSClientPlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(355)
    abstract fun bindNSClientV3Plugin(plugin: NSClientV3Plugin): PluginBase

    @Binds
    @NotNSClient
    @IntoMap
    @IntKey(360)
    abstract fun bindTidepoolPlugin(plugin: TidepoolPlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(364)
    abstract fun bindXdripPlugin(plugin: XdripPlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(366)
    abstract fun bindDataBroadcastPlugin(plugin: DataBroadcastPlugin): PluginBase

    @Binds
    @NotNSClient
    @IntoMap
    @IntKey(368)
    abstract fun bindsOpenHumansPlugin(plugin: OpenHumansUploaderPlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(370)
    abstract fun bindMaintenancePlugin(plugin: MaintenancePlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(380)
    abstract fun bindDstHelperPlugin(plugin: info.nightscout.plugins.constraints.dstHelper.DstHelperPlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(381)
    abstract fun bindBgQualityCheckPlugin(plugin: BgQualityCheckPlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(400)
    abstract fun bindXdripSourcePlugin(plugin: XdripSourcePlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(410)
    abstract fun bindNSClientSourcePlugin(plugin: NSClientSourcePlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(420)
    abstract fun bindMM640gPlugin(plugin: MM640gPlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(430)
    abstract fun bindGlimpPlugin(plugin: GlimpPlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(440)
    abstract fun bindDexcomPlugin(plugin: DexcomPlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(450)
    abstract fun bindPoctechPlugin(plugin: PoctechPlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(460)
    abstract fun bindTomatoPlugin(plugin: TomatoPlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(465)
    abstract fun bindAidexPlugin(plugin: AidexPlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(470)
    abstract fun bindGlunovoPlugin(plugin: GlunovoPlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(473)
    abstract fun bindIntelligoPlugin(plugin: IntelligoPlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(475)
    abstract fun bindRandomBgPlugin(plugin: RandomBgPlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(490)
    abstract fun bindConfigBuilderPlugin(plugin: ConfigBuilderPlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(500)
    abstract fun bindThemeSwitcherPlugin(plugin: ThemeSwitcherPlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(600)
    abstract fun bindNoSmoothingPlugin(plugin: NoSmoothingPlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(605)
    abstract fun bindExponentialSmoothingPlugin(plugin: ExponentialSmoothingPlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(610)
    abstract fun bindAvgSmoothingPlugin(plugin: AvgSmoothingPlugin): PluginBase

    @Qualifier
    annotation class AllConfigs

    @Qualifier
    annotation class PumpDriver

    @Qualifier
    annotation class NotNSClient

    @Qualifier
    annotation class APS

    @Qualifier
    annotation class Unfinished
}