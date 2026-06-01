package app.aaps.di

import android.app.Application
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

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface TestAppEntryPoint {

        fun androidInjector(): DispatchingAndroidInjector<Any>
    }

    override fun androidInjector(): AndroidInjector<Any> =
        EntryPointAccessors.fromApplication(this, TestAppEntryPoint::class.java).androidInjector()
}

@CustomTestApplication(BaseTestApp::class)
interface HiltTestApplication
