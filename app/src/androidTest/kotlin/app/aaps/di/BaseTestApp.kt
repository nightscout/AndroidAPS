package app.aaps.di

import android.app.Application
import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import app.aaps.core.interfaces.di.MetroMemberInjector
import app.aaps.core.ui.compose.MetroViewModelFactoryOwner
import app.aaps.di.metro.MetroGraphs
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.android.AndroidInjector
import dagger.android.DispatchingAndroidInjector
import dagger.android.HasAndroidInjector
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.testing.CustomTestApplication
import dagger.hilt.components.SingletonComponent
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory

/**
 * Base application for instrumented tests. Mirrors [app.aaps.MainApp]'s dagger-android bridge so that
 * production code resolving `(applicationContext as HasAndroidInjector).androidInjector()` keeps working
 * under Hilt. [CustomTestApplication] generates the Hilt-enabled `HiltTestApplication_Application`
 * (named after the annotated interface) used by the test runner.
 *
 * Under Hilt instrumented tests the singleton component is created per test by `HiltAndroidRule`, so it
 * does not exist in [onCreate]. Therefore this app does no graph access at startup — the plugin/config
 * initialization that MainApp does in onCreate is performed instead in [app.aaps.HiltInstrumentedTest]
 * after the rule has built the component. [androidInjector] resolves the injector freshly per call (no
 * caching) so it always targets the current test's component.
 */
open class BaseTestApp : Application(), HasAndroidInjector, MetroMemberInjector, MetroViewModelFactoryOwner {

    override fun onCreate() {
        super.onCreate()
        // Instrumented tests run under the production applicationId with Firebase auto-initialized (via
        // FirebaseInitProvider, before onCreate), so a crash on a CI emulator — e.g. an activity launched
        // outside a HiltAndroidRule scope whose graph access then fails (RequestDexcomPermissionActivity /
        // "The component was not created") — would be reported to the PRODUCTION Crashlytics dashboard.
        // FabricPrivacyImpl normally gates collection, but it only runs once injected, which is too late
        // for (and unrelated to) test crashes. Disable collection here so test noise never reaches the dashboard.
        FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = false
        // Production WorkManager init lives in MainApp (Configuration.Provider) + the default
        // androidx.startup initializer is removed from the manifest. Neither applies under the Hilt
        // test application, so initialize a test WorkManager here — otherwise building the Hilt graph
        // (e.g. SyncModule.providesWorkManager → WorkManager.getInstance) throws "not initialized".
        //
        // The factory must be the Hilt one: @HiltWorker workers (e.g. QueueWorker) are built via
        // assisted injection and cannot be instantiated by WorkManager's default reflective factory
        // ("Could not instantiate ... NoSuchMethodException"), which would leave queued commands
        // (e.g. CommandSetProfile) forever unexecuted. The Hilt singleton component does not exist yet
        // at onCreate (HiltAndroidRule builds it per test), so resolve HiltWorkerFactory lazily via an
        // EntryPoint at worker-creation time — by then the graph is built. Returning its result (null
        // for non-@HiltWorker workers) lets WorkManager's built-in reflective fallback handle legacy
        // workers, exactly as MainApp's `setWorkerFactory(hiltWorkerFactory)` does in production.
        val configuration = Configuration.Builder()
            .setExecutor(SynchronousExecutor())
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker? =
                    EntryPointAccessors.fromApplication(this@BaseTestApp, WorkerFactoryEntryPoint::class.java)
                        .hiltWorkerFactory()
                        .createWorker(appContext, workerClassName, workerParameters)
            })
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(this, configuration)
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface TestAppEntryPoint {

        fun androidInjector(): DispatchingAndroidInjector<Any>
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WorkerFactoryEntryPoint {

        fun hiltWorkerFactory(): HiltWorkerFactory
    }

    /**
     * The Metro half of the bridge, resolved lazily for the same reason as [WorkerFactoryEntryPoint]:
     * the singleton component is built per test by `HiltAndroidRule`, so it does not exist in
     * [onCreate]. `MetroGraphs` is `@Singleton`, so every call here returns the current test's one root.
     */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface MetroBridgeEntryPoint {

        fun metroGraphs(): MetroGraphs
    }

    override fun androidInjector(): AndroidInjector<Any> =
        EntryPointAccessors.fromApplication(this, TestAppEntryPoint::class.java).androidInjector()

    private fun metroGraphs(): MetroGraphs =
        EntryPointAccessors.fromApplication(this, MetroBridgeEntryPoint::class.java).metroGraphs()

    // Mirrors MainApp. Without these, anything Metro injects crashes the moment the system creates it
    // outside a test - a started service or a broadcast receiver - because the application it looks at
    // is this one, not MainApp. `DummyService` did exactly that and took the whole run down with it.
    override fun injectMembers(target: Any): Boolean {
        val graphs = try {
            metroGraphs()
        } catch (_: IllegalStateException) {
            // No component yet, so no test is running - `HiltAndroidRule` builds one per test. The
            // system can still deliver a broadcast at such a moment: MY_PACKAGE_REPLACED arrives right
            // after `adb install -r`, and it reached AutoStartReceiver here. In production
            // `injectMetroMembers` rightly kills the process for a target it cannot inject, but doing
            // that here fails a whole run over a broadcast no test asked for. Report success instead
            // and leave the fields unset: nothing is running that could read them.
            return true
        }
        // A genuine missing binding inside a test still fails loudly, which is the point of the check.
        return graphs.injectMembers(target)
    }

    override val metroViewModelFactory: MetroViewModelFactory get() = metroGraphs().viewModelFactory
}

@CustomTestApplication(BaseTestApp::class)
interface HiltTestApplication
