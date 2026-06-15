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
import dagger.android.AndroidInjector
import dagger.android.DispatchingAndroidInjector
import dagger.android.HasAndroidInjector
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.testing.CustomTestApplication
import dagger.hilt.components.SingletonComponent

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
open class BaseTestApp : Application(), HasAndroidInjector {

    override fun onCreate() {
        super.onCreate()
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

    override fun androidInjector(): AndroidInjector<Any> =
        EntryPointAccessors.fromApplication(this, TestAppEntryPoint::class.java).androidInjector()
}

@CustomTestApplication(BaseTestApp::class)
interface HiltTestApplication
