package app.aaps.di

import android.app.Application
import android.content.Context
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import app.aaps.di.testGraphs
import app.aaps.core.interfaces.di.MetroMemberInjector
import app.aaps.core.interfaces.resources.TextRefIdRegistry
import app.aaps.core.ui.CoreUiStringIds
import app.aaps.implementation.ImplementationStringIds
import app.aaps.core.ui.compose.MetroViewModelFactoryOwner
import app.aaps.di.metro.MetroGraphs
import app.aaps.database.di.DatabaseConfig
import app.aaps.plugins.aps.ApsStringIds
import app.aaps.plugins.automation.AutomationStringIds
import app.aaps.plugins.calibration.CalibrationStringIds
import app.aaps.plugins.configuration.ConfigurationStringIds
import app.aaps.plugins.constraints.ConstraintsStringIds
import app.aaps.plugins.main.MainStringIds
import app.aaps.plugins.sensitivity.SensitivityStringIds
import app.aaps.plugins.sync.SyncStringIds
import app.aaps.plugins.source.SourceStringIds
import app.aaps.plugins.smoothing.SmoothingStringIds
import app.aaps.pump.virtual.VirtualStringIds
import app.aaps.ui.UiStringIds
import com.google.firebase.Firebase
import com.google.firebase.analytics.analytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory

/**
 * Base application for instrumented tests. Mirrors [app.aaps.MainApp]: both build the one Metro root
 * themselves and act as its member injector.
 *
 * It used to be wrapped by `@CustomTestApplication(BaseTestApp::class)`, whose generated
 * `HiltTestApplication_Application` the runner installed, and the graph could not exist in [onCreate]
 * because `HiltAndroidRule` built a Hilt component per test. That is gone: `AapsTestRunner` installs
 * this class directly and the graph lives for the whole process, so [onCreate] can use it.
 */
open class BaseTestApp : Application(), MetroMemberInjector, MetroViewModelFactoryOwner {

    /**
     * The Metro root for the test process, built here exactly as `MainApp` builds its own.
     *
     * It used to come from Hilt, through an `@EntryPoint`, and could not exist until `HiltAndroidRule`
     * had built a component per test - which is why everything used to resolve it lazily and cope with
     * it being absent.
     *
     * The two arguments are the whole of how a test graph differs from the real one.
     */
    @Volatile private var metroGraphsOrNull: MetroGraphs? = null

    private val metroGraphs: MetroGraphs
        get() = metroGraphsOrNull ?: newGraph().also { metroGraphsOrNull = it }

    private fun newGraph() = MetroGraphs(
        context = this,
        memberInjector = this,
        databaseConfig = DatabaseConfig.IN_MEMORY,
        externalOptionsOverride = ExternalOptionsOverride { EmulatedOptions.enabled }
    )

    /**
     * Throw the graph away so the next read builds a fresh one. Called between tests by [ResetGraphRule].
     *
     * **This is not a tidiness measure, it replaces something the removed Hilt rule was doing.**
     * `HiltAndroidRule` built a new component - and so a new Metro root - for every test method. Some
     * objects read `config.isEnabled` exactly once, when they are constructed: `RfcommTransport` and
     * `BleTransport` pick the emulator or the real transport there and never look again. The Dana tests
     * select a different pump variant per test method, so without a fresh graph every test after the
     * first would silently drive the *first* test's transport.
     *
     * There is no Android Test Orchestrator here, so the whole run shares one process and one
     * application - the graph would otherwise live for every test in the run.
     */
    fun resetGraph() {
        metroGraphsOrNull = null
    }

    override fun onCreate() {
        super.onCreate()
        // The same owners MainApp registers. This application replaces MainApp for instrumented tests,
        // so without this every TextRef.Named has no id to resolve to and the screens render blank
        // text - which fails as "the text is not displayed", a long way from the cause.
        // `coreUi` and `implementation` are here now too. ResourceHelperImpl used to register them from
        // its own constructor; that ran at an undefined moment and kept the class on Dagger, so it moved
        // to an explicit start() called by the Application - which means this app has to do it as well.
        registerStringOwners()
        TextRefIdRegistry.register("coreUi") { name -> CoreUiStringIds.idOf(name) }
        TextRefIdRegistry.register("implementation") { name -> ImplementationStringIds.idOf(name) }
        // Instrumented tests run under the production applicationId with Firebase auto-initialized (via
        // FirebaseInitProvider, before onCreate), so a crash on a CI emulator — e.g. an activity launched
        // outside a test scope whose graph access then fails (RequestDexcomPermissionActivity /
        // "The component was not created") — would be reported to the PRODUCTION Crashlytics dashboard.
        // FabricPrivacyImpl normally gates collection, but it only runs once injected, which is too late
        // for (and unrelated to) test crashes. Disable collection here so test noise never reaches the dashboard.
        FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = false
        // Analytics has exactly the same exposure and was missed above: it is on by default after the
        // auto-init, and the only thing that ever turns it off is FabricPrivacyImpl's init - which runs
        // when that class is injected, far too late here and often not at all. Every CI run therefore
        // registered the emulator as a real user of the production app id, which is why a dev build
        // showed dozens of "users" in the console.
        Firebase.analytics.setAnalyticsCollectionEnabled(false)
        // Production WorkManager init lives in MainApp (Configuration.Provider) + the default
        // androidx.startup initializer is removed from the manifest. Neither applies to this test
        // application, so initialize a test WorkManager here - otherwise anything reaching
        // WorkManager.getInstance throws "not initialized".
        //
        // Every worker is built by assisted injection, so WorkManager's default reflective factory
        // cannot instantiate any of them ("Could not instantiate ... NoSuchMethodException") - that
        // would leave queued commands (e.g. CommandSetProfile) forever unexecuted. This mirrors
        // production's `MetroWorkerFactory`.
        val configuration = Configuration.Builder()
            .setExecutor(SynchronousExecutor())
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker? =
                    metroGraphs.workerCreators()[workerClassName]?.create(appContext, workerParameters)
            })
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(this, configuration)
    }

    /** What a test reads its objects from, in place of the `@Inject` fields Hilt used to fill. */
    val graphs: MetroGraphs get() = metroGraphs

    // Mirrors MainApp. Without these, anything Metro injects crashes the moment the system creates it
    // outside a test - a started service or a broadcast receiver - because the application it looks at
    // is this one, not MainApp. `DummyService` did exactly that and took the whole run down with it.
    //
    // This used to swallow an IllegalStateException, because between tests there was no Hilt component
    // and a broadcast could still arrive - MY_PACKAGE_REPLACED lands right after `adb install -r` and
    // reached AutoStartReceiver. That cannot happen now: the application owns the graph, so it exists
    // for the whole process and a missing binding fails loudly, which is the point of the check.
    override fun injectMembers(target: Any): Boolean = metroGraphs.injectMembers(target)

    override val metroViewModelFactory: MetroViewModelFactory get() = metroGraphs.viewModelFactory

    /**
     * Keep in step with `MainApp.registerStringOwners`. Each of these modules generates its own name
     * to `R.string` id map, and the registry is what a `TextRef.Named` is resolved through.
     */
    private fun registerStringOwners() {
        TextRefIdRegistry.register("virtual") { name -> VirtualStringIds.idOf(name) }
        TextRefIdRegistry.register("smoothing") { name -> SmoothingStringIds.idOf(name) }
        TextRefIdRegistry.register("source") { name -> SourceStringIds.idOf(name) }
        TextRefIdRegistry.register("calibration") { name -> CalibrationStringIds.idOf(name) }
        TextRefIdRegistry.register("sensitivity") { name -> SensitivityStringIds.idOf(name) }
        TextRefIdRegistry.register("main") { name -> MainStringIds.idOf(name) }
        TextRefIdRegistry.register("ui") { name -> UiStringIds.idOf(name) }
        TextRefIdRegistry.register("automation") { name -> AutomationStringIds.idOf(name) }
        TextRefIdRegistry.register("configuration") { name -> ConfigurationStringIds.idOf(name) }
        TextRefIdRegistry.register("constraints") { name -> ConstraintsStringIds.idOf(name) }
        TextRefIdRegistry.register("sync") { name -> SyncStringIds.idOf(name) }
        TextRefIdRegistry.register("aps") { name -> ApsStringIds.idOf(name) }
    }
}

