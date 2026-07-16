package app.aaps.di

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.ExternalOptions
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.history.HistoryBrowserData
import app.aaps.implementations.ConfigImpl
import app.aaps.implementations.UiInteractionImpl
import app.aaps.ui.compose.history.HistoryScope
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.android.AndroidInjectionModule
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

/**
 * Which [ExternalOptions] an instrumented test wants reported as enabled.
 *
 * In production these are toggled by dropping a marker file into the AAPS "extra" directory, which
 * [ConfigImpl.isEnabled] looks up via `FileListProvider.ensureExtraDirExists()`. That path is
 * unreachable in-process: it resolves a SAF *tree* URI (`DocumentFile.fromTreeUri`) taken from the
 * `AapsDirectoryUri` preference, and the instrumented tests deliberately never grant an AAPS
 * directory (there is no way to grant a SAF tree without driving the system picker). So the flags
 * always read false and no pump emulator could ever be selected.
 *
 * Set this **before** `hiltRule.inject()` — the transport is `@Singleton`, so the value is read once
 * when the graph first constructs it (see [EmulatedOptionsConfig]).
 */
object EmulatedOptions {

    @Volatile var enabled: Set<ExternalOptions> = emptySet()
}

/**
 * [Config] decorator that additionally reports whatever [EmulatedOptions] asks for.
 *
 * Delegates everything else to the real [ConfigImpl] — including `initProgressFlow` /
 * `initCompleted()`, so the instance a test flips is still the one the UI observes. Options not in
 * [EmulatedOptions] fall through to the production file lookup (which simply returns false without
 * an AAPS directory).
 */
class EmulatedOptionsConfig(private val delegate: Config) : Config by delegate {

    override fun isEnabled(option: ExternalOptions): Boolean =
        option in EmulatedOptions.enabled || delegate.isEnabled(option)
}

/**
 * Rebinds [Config] to [EmulatedOptionsConfig] so instrumented tests can drive the in-tree pump
 * emulators (`app/src/withPumps/.../DanaModules.provideBleTransport` picks `EmulatorBleTransport`
 * over the real one purely on `config.isEnabled(EMULATE_*)`).
 *
 * Replaces [AppModule] as well as [AppModule.AppBindings]: `AppBindings` is pulled in by
 * `AppModule`'s `includes`, so replacing it alone would leave the original `Config` binding in the
 * graph and collide with this one. `AppModule.Provide` carries its own `@InstallIn` and so survives
 * on its own; `AndroidInjectionModule` does not, and is re-included here.
 */
@Module(includes = [AndroidInjectionModule::class])
@TestInstallIn(components = [SingletonComponent::class], replaces = [AppModule::class, AppModule.AppBindings::class])
abstract class TestAppBindingsInstallModule {

    @Binds abstract fun bindActivityNames(activityNames: UiInteractionImpl): UiInteraction

    @Binds @Singleton abstract fun bindHistoryScope(impl: HistoryBrowserData): HistoryScope

    companion object {

        @Provides
        @Singleton
        fun provideConfig(impl: ConfigImpl): Config = EmulatedOptionsConfig(impl)
    }
}
