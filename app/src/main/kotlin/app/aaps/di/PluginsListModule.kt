package app.aaps.di

import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.plugins.aps.autotune.AutotunePlugin
import app.aaps.plugins.aps.loop.LoopPlugin
import app.aaps.plugins.aps.openAPSAMA.OpenAPSAMAPlugin
import app.aaps.plugins.aps.openAPSAutoISF.OpenAPSAutoISFPlugin
import app.aaps.plugins.aps.openAPSSMB.OpenAPSSMBPlugin
import app.aaps.plugins.automation.AutomationPlugin
import app.aaps.plugins.configuration.configBuilder.ConfigBuilderPlugin
import app.aaps.plugins.configuration.maintenance.MaintenancePlugin
import app.aaps.plugins.constraints.bgQualityCheck.BgQualityCheckPlugin
import app.aaps.plugins.constraints.dstHelper.DstHelperPlugin
import app.aaps.plugins.constraints.objectives.ObjectivesPlugin
import app.aaps.plugins.constraints.safety.SafetyPlugin
import app.aaps.plugins.constraints.signatureVerifier.SignatureVerifierPlugin
import app.aaps.plugins.constraints.storage.StorageConstraintPlugin
import app.aaps.plugins.constraints.versionChecker.VersionCheckerPlugin
import app.aaps.plugins.insulin.InsulinLyumjevPlugin
import app.aaps.plugins.insulin.InsulinOrefFreePeakPlugin
import app.aaps.plugins.insulin.InsulinOrefRapidActingPlugin
import app.aaps.plugins.insulin.InsulinOrefUltraRapidActingPlugin
import app.aaps.plugins.main.general.actions.ActionsPlugin
import app.aaps.plugins.main.general.food.FoodPlugin
import app.aaps.plugins.main.general.overview.OverviewPlugin
import app.aaps.plugins.main.general.persistentNotification.PersistentNotificationPlugin
import app.aaps.plugins.main.general.smsCommunicator.SmsCommunicatorPlugin
import app.aaps.plugins.main.general.themes.ThemeSwitcherPlugin
import app.aaps.plugins.main.iob.iobCobCalculator.IobCobCalculatorPlugin
import app.aaps.plugins.main.profile.ProfilePlugin
import app.aaps.plugins.sensitivity.SensitivityAAPSPlugin
import app.aaps.plugins.sensitivity.SensitivityOref1Plugin
import app.aaps.plugins.sensitivity.SensitivityWeightedAveragePlugin
import app.aaps.plugins.smoothing.AvgSmoothingPlugin
import app.aaps.plugins.smoothing.ExponentialSmoothingPlugin
import app.aaps.plugins.smoothing.NoSmoothingPlugin
import app.aaps.plugins.source.DexcomPlugin
import app.aaps.plugins.source.GlimpPlugin
import app.aaps.plugins.source.GlunovoPlugin
import app.aaps.plugins.source.IntelligoPlugin
import app.aaps.plugins.source.MM640gPlugin
import app.aaps.plugins.source.NSClientSourcePlugin
import app.aaps.plugins.source.PatchedSiAppPlugin
import app.aaps.plugins.source.PatchedSinoAppPlugin
import app.aaps.plugins.source.PoctechPlugin
import app.aaps.plugins.source.RandomBgPlugin
import app.aaps.plugins.source.SyaiPlugin
import app.aaps.plugins.source.TomatoPlugin
import app.aaps.plugins.source.XdripSourcePlugin
import app.aaps.plugins.sync.garmin.GarminPlugin
import app.aaps.plugins.sync.nsclient.NSClientPlugin
import app.aaps.plugins.sync.nsclientV3.NSClientV3Plugin
import app.aaps.plugins.sync.openhumans.OpenHumansUploaderPlugin
import app.aaps.plugins.sync.tidepool.TidepoolPlugin
import app.aaps.plugins.sync.tizen.TizenPlugin
import app.aaps.plugins.sync.wear.WearPlugin
import app.aaps.plugins.sync.xdrip.XdripPlugin
import app.aaps.pump.danar.DanaRPlugin
import app.aaps.pump.danarkorean.DanaRKoreanPlugin
import app.aaps.pump.danars.DanaRSPlugin
import app.aaps.pump.danarv2.DanaRv2Plugin
import app.aaps.pump.diaconn.DiaconnG8Plugin
import app.aaps.pump.eopatch.EopatchPumpPlugin
import app.aaps.pump.equil.EquilPumpPlugin
import app.aaps.pump.insight.InsightPlugin
import app.aaps.pump.medtronic.MedtronicPumpPlugin
import app.aaps.pump.medtrum.MedtrumPlugin
import app.aaps.pump.omnipod.dash.OmnipodDashPumpPlugin
import app.aaps.pump.omnipod.eros.OmnipodErosPumpPlugin
import app.aaps.pump.virtual.VirtualPumpPlugin
import app.aaps.pump.apex.ApexPumpPlugin
import dagger.Binds
import dagger.Module
import dagger.multibindings.IntKey
import dagger.multibindings.IntoMap
import info.nightscout.pump.combov2.ComboV2Plugin
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
    abstract fun bindDanaRSPlugin(plugin: DanaRSPlugin): PluginBase

    @Binds
    @PumpDriver
    @IntoMap
    @IntKey(130)
    abstract fun bindLocalInsightPlugin(plugin: InsightPlugin): PluginBase

    @Binds
    @PumpDriver
    @IntoMap
    @IntKey(140)
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
    @PumpDriver
    @IntoMap
    @IntKey(160)
    abstract fun bindMedtrumPlugin(plugin: MedtrumPlugin): PluginBase

    @Binds
    @PumpDriver
    @IntoMap
    @IntKey(170)
    abstract fun bindEquilPumpPlugin(plugin: EquilPumpPlugin): PluginBase

    @Binds
    @PumpDriver
    @IntoMap
    @IntKey(171)
    abstract fun bindApexPumpPlugin(plugin: ApexPumpPlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(180)
    abstract fun bindVirtualPumpPlugin(plugin: VirtualPumpPlugin): PluginBase

    @Binds
    @APS
    @IntoMap
    @IntKey(190)
    abstract fun bindLoopPlugin(plugin: LoopPlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(210)
    abstract fun bindOpenAPSAMAPlugin(plugin: OpenAPSAMAPlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(220)
    abstract fun bindOpenAPSSMBPlugin(plugin: OpenAPSSMBPlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(225)
    abstract fun bindOpenAPSAutoISFPlugin(plugin: OpenAPSAutoISFPlugin): PluginBase

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
    abstract fun bindVersionCheckerPlugin(plugin: VersionCheckerPlugin): PluginBase

    @Binds
    @NotNSClient
    @IntoMap
    @IntKey(280)
    abstract fun bindSmsCommunicatorPlugin(plugin: SmsCommunicatorPlugin): PluginBase

    @Binds
    @APS
    @IntoMap
    @IntKey(290)
    abstract fun bindStorageConstraintPlugin(plugin: StorageConstraintPlugin): PluginBase

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
    @NotNSClient
    @IntoMap
    @IntKey(366)
    abstract fun bindsOpenHumansPlugin(plugin: OpenHumansUploaderPlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(367)
    abstract fun bindWearPlugin(plugin: WearPlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(368)
    abstract fun bindDataBroadcastPlugin(plugin: TizenPlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(369)
    abstract fun bindGarminPlugin(plugin: GarminPlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(370)
    abstract fun bindMaintenancePlugin(plugin: MaintenancePlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(380)
    abstract fun bindDstHelperPlugin(plugin: DstHelperPlugin): PluginBase

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
    @IntKey(474)
    abstract fun bindSyaiPlugin(plugin: SyaiPlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(475)
    abstract fun bindPatchedSiAppPlugin(plugin: PatchedSiAppPlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(476)
    abstract fun bindPatchedSinoAppPlugin(plugin: PatchedSinoAppPlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(479)
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