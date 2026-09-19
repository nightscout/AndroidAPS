package app.aaps.di.metro

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Which graph accessors hand back the **same instance** on two reads, pinned.
 * In `src/testFull`, not `src/test`: it pins the `full` graph, and `src/test` is compiled for every
 * flavour - including the followers, whose graph has no pumps at all. It would fail there by design,
 * which is exactly how `PumpDriverBucketTest` once went red on a follower unnoticed.
 * ## Why identity rather than annotations
 * Reading the annotation would only re-state the source. This reads the **graph**, so it is true
 * regardless of how ownership is spelled - a `@SingleIn` class, a scoped `@Provides` in a binding
 * container, or interop reading a javax scope all look the same from here, which is the point when the
 * whole change is swapping one spelling for another.
 * ## Reading the lists
 * [SINGLE_INSTANCE] is what must stay stable. [FRESH_EACH_READ] is deliberately unscoped - a value
 * object or a lookup where a second one costs nothing. Both are pinned: a type moving between them
 * fails, in either direction. Moving one on purpose means editing the list in the same change, which is
 * exactly the review moment this test is for.
 * A newly-throwing accessor would quietly shrink coverage, so [UNREADABLE] is pinned too.
 * ## Pump accessors get a rule, not a list
 * The accessors a pump module contributes (`DanaRAccessors`, `EquilAccessors`, ...) exist only when
 * that module is in the build. Pinning them by name made `settings.gradle` a dependency of this test:
 * removing `:pump:dana:danar` failed it. They are there so an instrumented test can watch the object
 * the pump writes to, and a second instance would be an object the pump never writes to - so the rule
 * is that every one of them is a single instance, whichever pumps are in the build. Only
 * [PUMP_UNREADABLE] is named, and only checked when that accessor is present.
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

        // No emptiness check: a broken scan would drop the pump names into `stable`, and the exact match
        // on SINGLE_INSTANCE below fails on them. A build with no pump accessors at all is legitimate.
        val pumpAccessors = pumpAccessorNames(root.javaClass)

        val stable = sortedSetOf<String>()
        val fresh = sortedSetOf<String>()
        val pumpFresh = sortedSetOf<String>()
        val unreadable = sortedSetOf<String>()

        for (accessor in accessors) {
            val name = accessor.name.removePrefix("get").replaceFirstChar { it.lowercase() }
            val first = runCatching { accessor.invoke(root) }.getOrElse { unreadable += name; continue }
            val second = runCatching { accessor.invoke(root) }.getOrElse { unreadable += name; continue }
            when {
                name in pumpAccessors -> if (first !== second) pumpFresh += name
                first === second      -> stable += name
                else                  -> fresh += name
            }
        }

        // One assertion per list, so a failure names which way a type moved rather than dumping all three.
        assertThat(stable).containsExactlyElementsIn(SINGLE_INSTANCE)
        assertThat(fresh).containsExactlyElementsIn(FRESH_EACH_READ)
        assertThat(pumpFresh).isEmpty()
        assertThat(unreadable).containsExactlyElementsIn(UNREADABLE + PUMP_UNREADABLE.filter { it in pumpAccessors })
    }

    /** Names of the accessors declared on an interface that a pump module contributes to the graph. */
    private fun pumpAccessorNames(graph: Class<*>): Set<String> =
        allInterfaces(graph)
            .filter { it.name.startsWith("app.aaps.pump.") || it.name.startsWith("info.nightscout.pump.") }
            .flatMap { it.declaredMethods.toList() }
            .filter { it.parameterCount == 0 && it.name.startsWith("get") }
            .map { it.name.removePrefix("get").replaceFirstChar { c -> c.lowercase() } }
            .toSet()

    private fun allInterfaces(type: Class<*>): Set<Class<*>> =
        generateSequence(type) { it.superclass }
            .flatMap { it.interfaces.asSequence() }
            .flatMap { sequenceOf(it) + allInterfaces(it) }
            .toSet()

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
            "exportPasswordDataStore",
            "fabricPrivacy",
            "fabricPrivacyImpl",
            "fileListProvider",
            "glucoseStatusCalculatorAutoIsf",
            "glucoseStatusCalculatorSMB",
            "glucoseStatusProvider",
            "graphConfigRepository",
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
            "snackbarNotificationFallback",
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
            "sourceGraph",
            "viewModelProviders",
            "widgetUpdater",
            "workersGraph",
        )

        /**
         * Cannot be built in a plain-JVM test: `workManager` needs WorkManager initialised. Pinned so a
         * newly-throwing accessor cannot quietly shrink this test's reach.
         *
         * `graphConfigRepository` used to be here, blamed on its DataStore file. That was the wrong
         * reason: the blocker was `Dispatchers.Main` in its constructor scope, so nothing could build
         * it without a main looper. `cbe022ba1b` moved that scope to the IO dispatcher and it builds
         * now, which is why it moved into [SINGLE_INSTANCE] - the list grew rather than shrank.
         */
        val UNREADABLE = setOf("workManager")

        /**
         * Pump accessors that cannot be built in a plain-JVM test: the Dana RS `bleTransport` reaches
         * Bluetooth. Checked only when its module is in the build, so naming it here is a string, not a
         * dependency.
         */
        val PUMP_UNREADABLE = setOf("bleTransport")
    }
}
