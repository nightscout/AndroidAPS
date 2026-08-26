package app.aaps.di

import android.content.Context
import android.telephony.SmsManager
import androidx.work.WorkManager
import android.content.SharedPreferences
import app.aaps.core.interfaces.alerts.LocalAlertUtils
import app.aaps.core.interfaces.aps.APSResult
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.automation.Automation
import app.aaps.core.interfaces.autotune.Autotune
import app.aaps.core.interfaces.bgQualityCheck.BgQualityCheck
import app.aaps.core.interfaces.bolus.BatchExecutor
import app.aaps.core.interfaces.bolus.WizardBolusExecutor
import app.aaps.core.interfaces.bolus.WizardExecutor
import app.aaps.core.interfaces.clientcontrol.ClientControlActionDispatcher
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.constraints.Objectives
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.ProcessedTbrEbData
import app.aaps.core.interfaces.di.ApplicationScope
import app.aaps.core.interfaces.di.MetroMemberInjector
import app.aaps.core.interfaces.dst.DstHelper
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.insulin.InsulinManager
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.L
import app.aaps.core.interfaces.logging.LoggerUtils
import app.aaps.core.interfaces.overview.LastBgData
import app.aaps.core.interfaces.local.LocaleDependentSetting
import app.aaps.core.interfaces.protection.ExportPasswordDataStore
import app.aaps.core.interfaces.notifications.AlarmSoundPlayer
import app.aaps.plugins.sync.tidepool.comm.TidepoolUploader
import app.aaps.plugins.sync.tidepool.auth.AuthFlowOut
import app.aaps.core.interfaces.widget.WidgetUpdater
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.maintenance.CloudDirectoryManager
import app.aaps.core.interfaces.maintenance.FileListProvider
import app.aaps.core.interfaces.maintenance.ImportExportPrefs
import app.aaps.core.interfaces.maintenance.Maintenance
import app.aaps.core.interfaces.notifications.NotificationHolder
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.configuration.RunningConfigurationKeys
import app.aaps.core.interfaces.protection.SecureEncrypt
import app.aaps.core.interfaces.nsclient.NSClientRepository
import app.aaps.core.interfaces.nsclient.ProcessedDeviceStatusData
import app.aaps.core.interfaces.nsclient.StoreDataForDb
import app.aaps.core.interfaces.overview.OverviewData
import app.aaps.core.interfaces.overview.graph.GraphConfigRepository
import app.aaps.core.interfaces.overview.graph.OverviewDataCache
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginPermissions
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileRepository
import app.aaps.core.interfaces.profile.ProfileStore
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.profiling.Profiler
import app.aaps.core.interfaces.protection.PasswordCheck
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.pump.BlePreCheck
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.DetailedBolusInfoStorage
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpStatusProvider
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.TemporaryBasalStorage
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.receivers.ReceiverStatusStore
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.scenes.ActiveSceneSync
import app.aaps.core.interfaces.scenes.SceneActions
import app.aaps.core.interfaces.scenes.SceneAutomationApi
import app.aaps.core.interfaces.scenes.SceneChainResolver
import app.aaps.core.interfaces.scenes.SceneStore
import app.aaps.core.interfaces.scenes.Scenes
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.source.DexcomBoyda
import app.aaps.core.interfaces.source.NSClientSource
import app.aaps.core.interfaces.source.XDripSource
import app.aaps.core.interfaces.stats.DexcomTirCalculator
import app.aaps.core.interfaces.stats.TddCalculator
import app.aaps.core.interfaces.stats.TirCalculator
import app.aaps.core.interfaces.storage.Storage
import app.aaps.core.interfaces.sync.DataSyncSelectorXdrip
import app.aaps.core.interfaces.sync.NsClient
import app.aaps.core.interfaces.sync.XDripBroadcast
import app.aaps.core.interfaces.ui.CarbSuggestionActions
import app.aaps.core.interfaces.ui.IconsProvider
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.userEntry.UserEntryPresentationHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.interfaces.utils.Translator
import app.aaps.core.interfaces.utils.TrendCalculator
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.interfaces.versionChecker.VersionCheckerUtils
import app.aaps.core.interfaces.workflow.CalculationSignalsEmitter
import app.aaps.core.interfaces.workflow.CalculationWorkflow
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.nssdk.interfaces.RunningConfiguration
import app.aaps.core.keys.interfaces.VisibilityContext
import app.aaps.core.objects.crypto.CryptoUtil
import app.aaps.core.objects.runningMode.RunningModeGuard
import app.aaps.core.objects.wizard.BolusWizard
import app.aaps.core.objects.wizard.QuickWizard
import app.aaps.core.ui.search.SearchableProvider
import app.aaps.core.utils.receivers.DataInbox
import app.aaps.di.metro.AapsLeaves
import app.aaps.di.metro.MetroGraphs
import app.aaps.implementation.maintenance.cloud.CloudStorageManager
import app.aaps.implementation.scenes.ActiveSceneManager
import app.aaps.implementation.scenes.SceneExecutor
import app.aaps.plugins.aps.loop.runningMode.RunningModeExpiryJob
import app.aaps.plugins.aps.loop.runningMode.RunningModeReconciler
import app.aaps.plugins.aps.openAPS.DeltaCalculator
import app.aaps.plugins.aps.openAPSAMA.DetermineBasalAMA
import app.aaps.plugins.aps.openAPSAutoISF.DetermineBasalAutoISF
import app.aaps.plugins.aps.openAPSAutoISF.GlucoseStatusCalculatorAutoIsf
import app.aaps.plugins.aps.openAPSSMB.DetermineBasalSMB
import app.aaps.plugins.aps.openAPSSMB.GlucoseStatusCalculatorSMB
import app.aaps.plugins.automation.services.LastLocationDataContainer
import app.aaps.plugins.constraints.objectives.ObjectivesPlugin
import app.aaps.plugins.constraints.objectives.SntpClient
import app.aaps.plugins.constraints.signatureVerifier.SignatureVerifierPlugin
import app.aaps.ui.compose.history.HistoryScope
import app.aaps.ui.compose.overview.OverviewDataCacheFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Constructs the :core:objects classes.
 *
 * They carry no `@Inject constructor` of their own, because `javax.inject` is JVM only and would
 * keep that module from ever becoming multiplatform. Providing them here keeps the graph identical -
 * same scopes, same instances - and is the same trade already made for `BolusProgressData`.
 *
 * One such file per converted module, so that moving off this arrangement later is a per-module move.
 * :app is the one module that can never become multiplatform, so wiring put here never has to move
 * again; :implementation would probably also be safe, being the ANDROID implementation of
 * :core:interfaces, but that is a bet on the future and this needs no bet.
 *
 * Why no KMP module may carry a Dagger annotation - and why the mistake passes the build instead of
 * failing it - is in `_docs/KMP_IOS_FEASIBILITY.md`, under "Decisions taken".
 */
@Suppress("unused")
@Module
@InstallIn(SingletonComponent::class)
class CoreObjectsModule {

    @Suppress("DEPRECATION")
    @Provides
    fun smsManager(context: Context): SmsManager? = context.getSystemService(SmsManager::class.java)

    @Provides @Singleton fun provideCryptoUtil(graphs: MetroGraphs): CryptoUtil = graphs.cryptoUtil

    /*
     * Built by Metro now - see CoreObjectsGraph in :core:objects commonMain, which compiles for iOS.
     * These delegate so Dagger consumers keep working and there is exactly one instance.
     * CryptoUtil above is NOT delegated: its class is androidMain, so it cannot be in the graph.
     */
    @Provides @Singleton fun provideRunningModeGuard(graphs: MetroGraphs): RunningModeGuard = graphs.runningModeGuard

    @Provides @Singleton fun provideQuickWizard(graphs: MetroGraphs): QuickWizard = graphs.quickWizard

    @Provides fun provideBolusWizard(graphs: MetroGraphs): BolusWizard = graphs.bolusWizard

    /*
     * Three constraint plugins that are also bound to an interface. These delegates used to live in
     * `PluginsConstraintsModule`, inside `:plugins:constraints`; they moved here because that graph is
     * a `@GraphExtension` now and can only be opened from the root graph, which lives in this module.
     *
     * They must stay delegates rather than becoming a Dagger `@Binds`. A plugin behind an interface is
     * easy to miss when checking who still builds it - it does not look like an injection site - and
     * the result is two instances: the one in the plugin list, which is enabled and started, and an
     * unstarted twin handed to everyone who asks for the interface. That bug shipped once already.
     */
    @Provides @Singleton fun provideBgQualityCheck(graphs: MetroGraphs): BgQualityCheck = graphs.bgQualityCheck

    @Provides @Singleton fun provideDstHelper(graphs: MetroGraphs): DstHelper = graphs.dstHelper

    @Provides @Singleton fun provideObjectives(graphs: MetroGraphs): Objectives = graphs.objectives

    /*
     * By class as well as by interface, for the same reason as the signature verifier below: the
     * instrumented tests inject `ObjectivesPlugin` itself. Without this, Dagger sees the javax
     * `@Inject` constructor through interop and builds a second plugin - one the running app never
     * uses - so a test would set up objectives on an instance nothing reads.
     */
    @Provides @Singleton fun provideObjectivesPlugin(graphs: MetroGraphs): ObjectivesPlugin = graphs.objectivesPlugin

    /*
     * The signature verifier is asked for by class, not by interface: `MainApp` injects it for one
     * `shortHashes()` call and `PluginsConstraintsModule` reads `definition.json` through it. Metro
     * builds it now, so this delegate is what keeps those two from getting a second copy.
     */
    @Provides @Singleton fun provideSignatureVerifierPlugin(graphs: MetroGraphs): SignatureVerifierPlugin =
        graphs.signatureVerifier

    /*
     * The three BG-source plugins that are also bound to an interface. Same reasoning as above, and
     * these replace the `@Binds` that used to sit in `SourceModule` inside `:plugins:source` - that
     * module is gone now, because every source plugin registers itself with `@ContributesIntoMap`.
     */
    @Provides @Singleton fun provideXDripSource(graphs: MetroGraphs): XDripSource = graphs.xDripSource

    @Provides @Singleton fun provideNSClientSource(graphs: MetroGraphs): NSClientSource = graphs.nsClientSource

    @Provides @Singleton fun provideDexcomBoyda(graphs: MetroGraphs): DexcomBoyda = graphs.dexcomBoyda

    /*
     * Implementations that Metro builds now. `:implementation` used to @Binds these; the class carries
     * @ContributesBinding instead, so Metro owns the instance and Dagger consumers get it here. The
     * delegate is what stops Dagger building a second one.
     */
    @Provides @Singleton fun provideTrendCalculator(graphs: MetroGraphs): TrendCalculator = graphs.trendCalculator
    @Provides @Singleton fun provideCarbSuggestionActions(graphs: MetroGraphs): CarbSuggestionActions = graphs.carbSuggestionActions
    @Provides @Singleton fun provideTemporaryBasalStorage(graphs: MetroGraphs): TemporaryBasalStorage = graphs.temporaryBasalStorage
    @Provides @Singleton fun provideDetailedBolusInfoStorage(graphs: MetroGraphs): DetailedBolusInfoStorage = graphs.detailedBolusInfoStorage
    @Provides @Singleton fun provideBlePreCheck(graphs: MetroGraphs): BlePreCheck = graphs.blePreCheck
    @Provides @Singleton fun provideVisibilityContext(graphs: MetroGraphs): VisibilityContext = graphs.visibilityContext
    @Provides @Singleton fun provideCloudDirectoryManager(graphs: MetroGraphs): CloudDirectoryManager = graphs.cloudDirectoryManager
    @Provides @Singleton fun provideGraphConfigRepository(graphs: MetroGraphs): GraphConfigRepository = graphs.graphConfigRepository
    @Provides @Singleton fun provideBatchExecutor(graphs: MetroGraphs): BatchExecutor = graphs.batchExecutor

    @Provides @Singleton fun provideWizardBolusExecutor(graphs: MetroGraphs): WizardBolusExecutor =
        graphs.wizardBolusExecutor

    @Provides @Singleton fun provideLoggerUtils(graphs: MetroGraphs): LoggerUtils = graphs.loggerUtils
    @Provides @Singleton fun provideLastBgDataBinding(graphs: MetroGraphs): LastBgData = graphs.lastBgData
    @Provides @Singleton fun provideLocaleDependentSettingBinding(graphs: MetroGraphs): LocaleDependentSetting = graphs.localeDependentSetting
    @Provides @Singleton fun providePumpStatusProviderBinding(graphs: MetroGraphs): PumpStatusProvider = graphs.pumpStatusProvider
    @Provides @Singleton fun providePasswordCheckBinding(graphs: MetroGraphs): PasswordCheck = graphs.passwordCheck
    @Provides @Singleton fun provideOverviewDataBinding(graphs: MetroGraphs): OverviewData = graphs.overviewData
    @Provides @Singleton fun provideSharedPreferences(graphs: MetroGraphs): SharedPreferences = graphs.sharedPreferences
    @Provides @Singleton fun provideExportPasswordDataStoreBinding(graphs: MetroGraphs): ExportPasswordDataStore = graphs.exportPasswordDataStore
    @Provides @Singleton fun provideSecureEncryptBinding(graphs: MetroGraphs): SecureEncrypt = graphs.secureEncrypt
    @Provides @Singleton fun provideConcentrationHelperBinding(graphs: MetroGraphs): ConcentrationHelper = graphs.concentrationHelper
    @Provides @Singleton fun provideProcessedTbrEbDataBinding(graphs: MetroGraphs): ProcessedTbrEbData = graphs.processedTbrEbData
    @Provides @Singleton fun provideUserEntryLoggerBinding(graphs: MetroGraphs): UserEntryLogger = graphs.userEntryLogger
    @Provides @Singleton fun provideGlucoseStatusProviderBinding(graphs: MetroGraphs): GlucoseStatusProvider = graphs.glucoseStatusProvider
    @Provides @Singleton fun provideUserEntryPresentationHelperBinding(graphs: MetroGraphs): UserEntryPresentationHelper = graphs.userEntryPresentationHelper
    @Provides @Singleton fun provideNotificationHolderBinding(graphs: MetroGraphs): NotificationHolder = graphs.notificationHolder
    @Provides @Singleton fun provideAlarmSoundPlayerBinding(graphs: MetroGraphs): AlarmSoundPlayer = graphs.alarmSoundPlayer
    @Provides @Singleton fun provideProfilerBinding(graphs: MetroGraphs): Profiler = graphs.profiler
    @Provides @Singleton fun provideWizardExecutor(graphs: MetroGraphs): WizardExecutor = graphs.wizardExecutor
    @Provides @Singleton fun provideConfigBuilder(graphs: MetroGraphs): ConfigBuilder = graphs.configBuilder
    @Provides @Singleton fun provideDataSyncSelectorXdrip(graphs: MetroGraphs): DataSyncSelectorXdrip = graphs.dataSyncSelectorXdrip
    @Provides @Singleton fun provideActiveSceneSync(graphs: MetroGraphs): ActiveSceneSync = graphs.activeSceneSync
    @Provides @Singleton fun provideSceneChainResolver(graphs: MetroGraphs): SceneChainResolver = graphs.sceneChainResolver
    @Provides @Singleton fun provideSceneStore(graphs: MetroGraphs): SceneStore = graphs.sceneStore
    @Provides @Singleton fun provideScenes(graphs: MetroGraphs): Scenes = graphs.scenes
    @Provides @Singleton fun provideSceneActions(graphs: MetroGraphs): SceneActions = graphs.sceneActions

    /*
     * The live loop's calculator, from Metro, which builds it in `MainPluginsBindings`.
     *
     * Around fifty classes ask for this, plenty of them still Dagger-built - the instrumented tests
     * inject it directly. Without this they would get a second calculator, working from its own
     * autosens data while the loop used another.
     *
     * Note this is NOT the history browser's calculator. That one is scoped to a window in
     * `HistoryWindowGraph` and reaches its consumers through `HistoryBrowserData`, never through here.
     */
    @Provides @Singleton fun provideIobCobCalculator(graphs: MetroGraphs): IobCobCalculator = graphs.iobCobCalculator

    /*
     * The loop, from Metro.
     *
     * A reversal rather than a new delegate: `Loop` used to travel Dagger -> Metro through `AapsLeaves`,
     * because Dagger built `LoopPlugin`. Metro builds it now, so it travels the other way. Around fifty
     * classes ask for `Loop` and most are still Dagger-built, so the direction has to be right - two
     * loops would not fail anything, they would just both run.
     */
    @Provides @Singleton fun provideLoop(graphs: MetroGraphs): Loop = graphs.loop

    /*
     * The member injector, for classes Dagger builds that construct Metro-injected objects by hand.
     *
     * The Diaconn services are the first: they are still `DaggerService`s, but the packets they create
     * fill their fields from Metro's map now. `MainApp` implements this interface the same way for the
     * Android entry points; this is the same dispatch, reached through Dagger.
     */
    @Provides @Singleton fun provideMetroMemberInjector(graphs: MetroGraphs): MetroMemberInjector =
        object : MetroMemberInjector {
            override fun injectMembers(target: Any): Boolean = graphs.injectMembers(target)
        }

    /** Autotune, same story: Metro builds the plugin, the automation actions are still Dagger-built. */
    @Provides @Singleton fun provideAutotune(graphs: MetroGraphs): Autotune = graphs.autotune

    /*
     * The running-mode helpers, from Metro.
     *
     * They live in `:plugins:aps` commonMain, so they could not keep javax annotations - Metro's own are
     * the only ones that compile there. `MainApp` injects the reconciler through Dagger, so it needs the
     * delegate; the expiry job used to be an `AapsLeaves` leaf and now travels the other way.
     */
    @Provides @Singleton fun provideRunningModeReconciler(graphs: MetroGraphs): RunningModeReconciler =
        graphs.runningModeReconciler

    @Provides @Singleton fun provideRunningModeExpiryJob(graphs: MetroGraphs): RunningModeExpiryJob =
        graphs.runningModeExpiryJob

    /*
     * openAPS pieces, from Metro, which builds them in `:plugins:aps` commonMain.
     *
     * All six, because Dagger asks for all six. Two groups do: the instrumented APS tests
     * (`TestOpenAPSSMBPlugin`, `TestOpenAPSAMAPlugin`, `ReplayApsResultsTest`), which Hilt builds; and
     * the APS plugin providers still left in `ApsPluginsModule`, which take them as parameters. The
     * second group is easy to miss - they sit in the same file the providers were deleted from.
     *
     * Without these, interop would let Dagger build its own from the `@Inject` constructors and the loop
     * would score a decision with a different calculator than the one the app holds.
     */
    @Provides @Singleton fun provideGlucoseStatusCalculatorSMB(graphs: MetroGraphs): GlucoseStatusCalculatorSMB =
        graphs.glucoseStatusCalculatorSMB

    @Provides @Singleton fun provideDetermineBasalSMB(graphs: MetroGraphs): DetermineBasalSMB = graphs.determineBasalSMB

    @Provides @Singleton fun provideDetermineBasalAMA(graphs: MetroGraphs): DetermineBasalAMA = graphs.determineBasalAMA

    @Provides @Singleton fun provideDetermineBasalAutoISF(graphs: MetroGraphs): DetermineBasalAutoISF =
        graphs.determineBasalAutoISF

    @Provides @Singleton fun provideGlucoseStatusCalculatorAutoIsf(graphs: MetroGraphs): GlucoseStatusCalculatorAutoIsf =
        graphs.glucoseStatusCalculatorAutoIsf

    @Provides @Singleton fun provideDeltaCalculator(graphs: MetroGraphs): DeltaCalculator = graphs.deltaCalculator

    /*
     * The scene state holder, by class, from Metro.
     *
     * `SceneExecutor`, `SceneAutomationApiImpl` and `SceneExpiryWorker` are built by Dagger and ask for
     * `ActiveSceneManager` itself. The class carries Metro's `@SingleIn` and no javax scope, so without
     * this Dagger built its own - a NEW one per injection point, since an unscoped `@Inject` constructor
     * is not shared. Activating a scene then wrote to an object nothing was reading, and the overview
     * never showed the active scene. `@Singleton` here keeps Dagger's side to the one Metro built.
     */
    @Provides @Singleton fun provideActiveSceneManager(graphs: MetroGraphs): ActiveSceneManager =
        graphs.activeSceneManager
    @Provides @Singleton fun provideSceneAutomationApi(graphs: MetroGraphs): SceneAutomationApi = graphs.sceneAutomationApi

    @Provides @Singleton fun provideDecimalFormatter(graphs: MetroGraphs): DecimalFormatter = graphs.decimalFormatter

    @Provides @Singleton fun provideProfileUtil(graphs: MetroGraphs): ProfileUtil = graphs.profileUtil

    @Provides @Singleton fun provideHardLimits(graphs: MetroGraphs): HardLimits = graphs.hardLimits

    @Provides @Singleton fun provideStorage(graphs: MetroGraphs): Storage = graphs.storage

    @Provides @Singleton fun provideReceiverStatusStore(graphs: MetroGraphs): ReceiverStatusStore = graphs.receiverStatusStore

    @Provides @Singleton fun provideTranslator(graphs: MetroGraphs): Translator = graphs.translator
    @Provides @Singleton fun provideProtectionCheck(graphs: MetroGraphs): ProtectionCheck = graphs.protectionCheck
    @Provides @Singleton fun provideTddCalculator(graphs: MetroGraphs): TddCalculator = graphs.tddCalculator
    @Provides @Singleton fun provideTirCalculator(graphs: MetroGraphs): TirCalculator = graphs.tirCalculator
    @Provides @Singleton fun provideDexcomTirCalculator(graphs: MetroGraphs): DexcomTirCalculator = graphs.dexcomTirCalculator
    @Provides fun providePumpSync(graphs: MetroGraphs): PumpSync = graphs.pumpSync
    @Provides @Singleton fun provideIconsProvider(graphs: MetroGraphs): IconsProvider = graphs.iconsProvider
    @Provides @Singleton fun provideInsulinManager(graphs: MetroGraphs): InsulinManager = graphs.insulinManager
    @Provides @Singleton fun provideProfileRepository(graphs: MetroGraphs): ProfileRepository = graphs.profileRepository

    // Unscoped, as its Dagger @Binds was: a profile store is a value object built per caller.
    @Provides fun provideProfileStore(graphs: MetroGraphs): ProfileStore = graphs.profileStore
    /**
     * Builds [AapsLeaves] by hand rather than letting Dagger inject it.
     *
     * The class must NOT carry a javax `@Inject` constructor. With Dagger interop enabled in this
     * module, Metro would then see the container as something it can also construct, and the
     * compiler crashes with "Transforming after locked!" while transforming the very container it
     * is including. Constructing it here keeps the `@Inject` off the class and the crash away.
     */
    @Provides
    @Singleton
    @Suppress("LongParameterList")
    fun provideAapsLeaves(
        metroMemberInjectorProvider: Provider<MetroMemberInjector>,
        nsClientRepositoryProvider: Provider<NSClientRepository>,
        storeDataForDbProvider: Provider<StoreDataForDb>,
        runningConfigurationProvider: Provider<RunningConfiguration>,
        nsClientSourceProvider: Provider<NSClientSource>,
        runningConfigurationKeysProvider: Provider<RunningConfigurationKeys>,
        aapsLoggerProvider: Provider<AAPSLogger>,
        rxBusProvider: Provider<RxBus>,
        activePluginProvider: Provider<ActivePlugin>,
        @ApplicationScope appScopeProvider: Provider<CoroutineScope>,
        fabricPrivacyProvider: Provider<FabricPrivacy>,
        localAlertUtilsProvider: Provider<LocalAlertUtils>,
        persistenceLayerProvider: Provider<PersistenceLayer>,
        configProvider: Provider<Config>,
        calculationSignalsEmitterProvider: Provider<CalculationSignalsEmitter>,
        apsResultProvider: Provider<APSResult>,
        widgetUpdaterProvider: Provider<WidgetUpdater>,
        authFlowOutProvider: Provider<AuthFlowOut>,
        tidepoolUploaderProvider: Provider<TidepoolUploader>,
        dateUtilProvider: Provider<DateUtil>,
        profileFunctionProvider: Provider<ProfileFunction>,
        commandQueueProvider: Provider<CommandQueue>,
        maintenanceProvider: Provider<Maintenance>,
        rhProvider: Provider<ResourceHelper>,
        preferencesProvider: Provider<Preferences>,
        dstHelperProvider: Provider<DstHelper>,
        workManagerProvider: Provider<WorkManager>,
        notificationManagerProvider: Provider<NotificationManager>,
        sceneExecutorProvider: Provider<SceneExecutor>,
        fileListProviderProvider: Provider<FileListProvider>,
        dataInboxProvider: Provider<DataInbox>,
        cloudStorageManagerProvider: Provider<CloudStorageManager>,
        calculationWorkflowProvider: Provider<CalculationWorkflow>,
        overviewDataCacheFactoryProvider: Provider<OverviewDataCacheFactory>,
        constraintsCheckerProvider: Provider<ConstraintsChecker>,
        automationProvider: Provider<Automation>,
        processedDeviceStatusDataProvider: Provider<ProcessedDeviceStatusData>,
        contextProvider: Provider<Context>,
        uiInteractionProvider: Provider<UiInteraction>,
        lastLocationDataContainerProvider: Provider<LastLocationDataContainer>,
        versionCheckerUtilsProvider: Provider<VersionCheckerUtils>,
        searchableProvidersProvider: Provider<Set<SearchableProvider>>,
        aapsSchedulersProvider: Provider<AapsSchedulers>,
        spProvider: Provider<SP>,
        bolusProgressDataProvider: Provider<BolusProgressData>,
        pumpEnactResultProvider: Provider<PumpEnactResult>,
        historyScopeProvider: Provider<HistoryScope>,
        importExportPrefsProvider: Provider<ImportExportPrefs>,
        overviewDataCacheProvider: Provider<OverviewDataCache>,
        pluginPermissionsProvider: Provider<PluginPermissions>,
        lProvider: Provider<L>,
        @ApplicationContext appContextProvider: Provider<Context>,
        xDripBroadcastProvider: Provider<XDripBroadcast>,
        nsClientProvider: Provider<NsClient>,
        clientControlActionDispatcherProvider: Provider<ClientControlActionDispatcher>,
        sntpClientProvider: Provider<SntpClient>
    ): AapsLeaves = AapsLeaves(
        metroMemberInjectorProvider,
        nsClientRepositoryProvider,
        storeDataForDbProvider,
        runningConfigurationProvider,
        nsClientSourceProvider,
        runningConfigurationKeysProvider,
        aapsLoggerProvider,
        rxBusProvider,
        activePluginProvider,
        appScopeProvider,
        fabricPrivacyProvider,
        localAlertUtilsProvider,
        persistenceLayerProvider,
        configProvider,
        calculationSignalsEmitterProvider,
        apsResultProvider,
        widgetUpdaterProvider,
        authFlowOutProvider,
        tidepoolUploaderProvider,
        dateUtilProvider,
        profileFunctionProvider,
        commandQueueProvider,
        maintenanceProvider,
        rhProvider,
        preferencesProvider,
        dstHelperProvider,
        workManagerProvider,
        notificationManagerProvider,
        sceneExecutorProvider,
        fileListProviderProvider,
        dataInboxProvider,
        cloudStorageManagerProvider,
        calculationWorkflowProvider,
        overviewDataCacheFactoryProvider,
        constraintsCheckerProvider,
        automationProvider,
        processedDeviceStatusDataProvider,
        contextProvider,
        uiInteractionProvider,
        lastLocationDataContainerProvider,
        versionCheckerUtilsProvider,
        searchableProvidersProvider,
        aapsSchedulersProvider,
        spProvider,
        bolusProgressDataProvider,
        pumpEnactResultProvider,
        historyScopeProvider,
        importExportPrefsProvider,
        overviewDataCacheProvider,
        pluginPermissionsProvider,
        lProvider,
        appContextProvider,
        xDripBroadcastProvider,
        nsClientProvider,
        clientControlActionDispatcherProvider,
        sntpClientProvider
    )
}
