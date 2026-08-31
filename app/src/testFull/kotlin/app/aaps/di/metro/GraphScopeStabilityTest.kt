package app.aaps.di.metro

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Which graph accessors hand back the **same instance** on two reads, pinned.
 *
 * In `src/testFull`, not `src/test`: the pinned names include the pump bindings, and `src/test` is
 * compiled for every flavour - including the followers, whose graph has no pumps at all. It would fail
 * there by design, which is exactly how `PumpDriverBucketTest` once went red on a follower unnoticed.
 *
 * This exists for one change in particular: removing Metro's Dagger interop. With
 * `metro { interop { includeDagger() } }` on, Metro reads a `javax.inject.@Singleton` as a scope. Turn
 * interop off and every one of those has to have become `@SingleIn(AppScope::class)` first - and
 * missing one **does not fail to compile**. It silently becomes unscoped, so every injection point gets
 * its own copy.
 *
 * That is the most expensive bug class in this migration, and it is always silent: `ProfileSwitchSilentGate`
 * (a scene profile switch raised the notification the gate exists to hide), `ReceiverDelegate` (an
 * upload gate nothing updated), `RateLimit` (a limiter that never limited), `AutotuneIob`/`AutotuneFS`.
 * `SplitBrainTest` used to catch the Dagger-vs-Metro version of it and was retired with Dagger; this is
 * the guard for the version that survives, where Metro simply forgets a scope.
 *
 * ## Why identity rather than annotations
 *
 * Reading the annotation would only re-state the source. This reads the **graph**, so it is true
 * regardless of how ownership is spelled - a `@SingleIn` class, a scoped `@Provides` in a binding
 * container, or interop reading a javax scope all look the same from here, which is the point when the
 * whole change is swapping one spelling for another.
 *
 * ## Reading the lists
 *
 * [SINGLE_INSTANCE] is what must stay stable. [FRESH_EACH_READ] is deliberately unscoped - a value
 * object or a lookup where a second one costs nothing. Both are pinned: a type moving between them
 * fails, in either direction. Moving one on purpose means editing the list in the same change, which is
 * exactly the review moment this test is for.
 *
 * A newly-throwing accessor would quietly shrink coverage, so [UNREADABLE] is pinned too.
 */
class GraphScopeStabilityTest {

    @Test
    fun `graph scoping is unchanged`() {
        val root = testRoot()
        val accessors = root.javaClass.methods
            .filter { it.parameterCount == 0 && it.name.startsWith("get") && it.declaringClass != Any::class.java }
            // `foo$annotations` is a synthetic Kotlin method carrying the property's annotations. It is
            // not an accessor, returns void, and would sit in the pinned list looking meaningful.
            .filterNot { it.name.contains('$') }
            .distinctBy { it.name }

        check(accessors.size > 100) { "Only ${accessors.size} accessors found - the reflection broke" }

        val stable = sortedSetOf<String>()
        val fresh = sortedSetOf<String>()
        val unreadable = sortedSetOf<String>()

        for (accessor in accessors) {
            val name = accessor.name.removePrefix("get").replaceFirstChar { it.lowercase() }
            val first = runCatching { accessor.invoke(root) }.getOrElse { unreadable += name; continue }
            val second = runCatching { accessor.invoke(root) }.getOrElse { unreadable += name; continue }
            if (first === second) stable += name else fresh += name
        }

        // One assertion per list, so a failure names which way a type moved rather than dumping all three.
        assertThat(stable).containsExactlyElementsIn(SINGLE_INSTANCE)
        assertThat(fresh).containsExactlyElementsIn(FRESH_EACH_READ)
        assertThat(unreadable).containsExactlyElementsIn(UNREADABLE)
    }

    private companion object {

        /** Exactly one instance. A name leaving this list means something lost its scope. */
        val SINGLE_INSTANCE = setOf(
            "aapsLogger",
            "aapsSchedulers",
            "activePlugin",
            "activeSceneManager",
            "activeSceneSync",
            "activityMonitor",
            "alarmSoundPlayer",
            "appRepository",
            "appScope",
            "authFlowOut",
            "authorizedClientsRepository",
            "automation",
            "automationRuntime",
            "autotune",
            "batchExecutor",
            "bgQualityCheckPlugin",
            "blePreCheck",
            "bolusProgressData",
            "builtInSearchables",
            "calculationSignals",
            "calculationSignalsEmitter",
            "calculationWorkflow",
            "carbSuggestionActions",
            "clientControlActionDispatcher",
            "clientControlPublisher",
            "clientPairingRepository",
            "cloudDirectoryManager",
            "cloudStorageManager",
            "commandQueue",
            "concentrationHelper",
            "config",
            "configBuilder",
            "constraintsChecker",
            "cryptoUtil",
            "danaHistoryRecordDao",
            "danaPump",
            "danaRKoreanPlugin",
            "danaRPlugin",
            "danaRSPlugin",
            "danaRv2Plugin",
            "dataInbox",
            "dataSyncSelectorXdrip",
            "dateUtil",
            "decimalFormatter",
            "deltaCalculator",
            "detailedBolusInfoStorage",
            "determineBasalAMA",
            "determineBasalAutoISF",
            "determineBasalSMB",
            "dexcomPlugin",
            "dexcomTirCalculator",
            "dstHelper",
            "dstHelperPlugin",
            "equilBleTransport",
            "equilHistoryPumpDao",
            "equilHistoryRecordDao",
            "equilManager",
            "equilPumpPlugin",
            "exportPasswordDataStore",
            "fabricPrivacy",
            "fabricPrivacyImpl",
            "fileListProvider",
            "glucoseStatusCalculatorAutoIsf",
            "glucoseStatusCalculatorSMB",
            "glucoseStatusProvider",
            "hardLimits",
            "historyWindowFactory",
            "iconsProvider",
            "importExportPrefs",
            "insulinManager",
            "iobCobCalculator",
            "l",
            "lastBgData",
            "lastLocationDataContainer",
            "localAlertUtils",
            "localeDependentSetting",
            "loggerUtils",
            "loop",
            "maintenance",
            "manualAssistedFactoryProviders",
            "notificationHolder",
            "notificationManager",
            "nsClient",
            "nsClientRepository",
            "nsClientSource",
            "nsClientSourcePlugin",
            "nsClientV3Plugin",
            "nsIncomingDataProcessor",
            "objectivesPlugin",
            "openHumansMetroBridge",
            "overviewData",
            "overviewDataCache",
            "pairingOfferFetcher",
            "pairingOfferPublisher",
            "passwordCheck",
            "persistenceLayer",
            "pluginPermissions",
            "pluginStore",
            "preferences",
            "processedDeviceStatusData",
            "processedTbrEbData",
            "profileFunction",
            "profileRepository",
            "profileSwitchExpiryScheduler",
            "profileSwitchSilentGate",
            "profileUtil",
            "profiler",
            "protectionCheck",
            "pumpStatusProvider",
            "quickWizard",
            "rateLimit",
            "receiverDelegate",
            "receiverStatusStore",
            "resourceHelper",
            "resourceHelperImpl",
            "rfcommTransport",
            "runningConfiguration",
            "runningConfigurationKeys",
            "runningModeExpiryJob",
            "runningModeGuard",
            "runningModeReconciler",
            "rxBus",
            "sceneActions",
            "sceneAutomationApi",
            "sceneChainResolver",
            "sceneExecutor",
            "sceneIconResolver",
            "sceneStore",
            "scenes",
            "secureEncrypt",
            "sharedPreferences",
            "signatureVerifierPlugin",
            "smsCommunicatorPlugin",
            "smsCommunicatorRepository",
            "sp",
            "storage",
            "storeDataForDb",
            "tddCalculator",
            "temporaryBasalStorage",
            "tidepoolRepository",
            "tidepoolUploader",
            "tirCalculator",
            "translator",
            "trendCalculator",
            "uiInteraction",
            "userEntryLogger",
            "userEntryPresentationHelper",
            "versionCheckerUtils",
            "visibilityContext",
            "wearPlugin",
            "wizardBolusExecutor",
            "wizardExecutor",
            "workflowChainData",
            "xDripBroadcast",
            "xdripMvvmRepository",
            "xdripSourcePlugin",
        )

        /** Deliberately a new instance per read. */
        val FRESH_EACH_READ = setOf(
            "apsResult",
            "assistedFactoryProviders",
            "automationGraph",
            "autosensData",
            "bolusWizard",
            "cloudStorageProviders",
            "contributedApsPlugins",
            "contributedMemberInjectors",
            "contributedNotNsClientPlugins",
            "contributedPlugins",
            "contributedPumpDriverPlugins",
            "permissionProviders",
            "processLifecycleListener",
            "profileStore",
            "pumpEnactResult",
            "pumpSync",
            "pumpWithConcentration",
            "receiversGraph",
            "sourceGraph",
            "viewModelProviders",
            "widgetUpdater",
            "workersGraph",
        )

        /**
         * Cannot be built in a plain-JVM test. All three are Android lookups: `bleTransport` reaches
         * Bluetooth, `workManager` needs WorkManager initialised, `graphConfigRepository` opens a
         * DataStore file. Pinned so a newly-throwing accessor cannot quietly shrink this test's reach.
         */
        val UNREADABLE = setOf("bleTransport", "graphConfigRepository", "workManager")
    }
}
