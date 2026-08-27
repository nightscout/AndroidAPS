package app.aaps.di.metro

import app.aaps.core.interfaces.di.ApplicationScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import kotlinx.coroutines.CoroutineScope

/**
 * The application scope, owned by Metro.
 *
 * The qualified scope is a factory parameter of `AppRootGraph`, not a binding here: production passes
 * `SupervisorJob() + Dispatchers.Default`, and the unit tests pass an Unconfined one so that work
 * launched during graph construction runs on the calling thread. It used to be a Dagger `@Provides`
 * borrowed back through `AapsLeaves`.
 *
 * There must be exactly one. A second scope would not fail anything loudly: it would simply never be
 * cancelled with the first, so work started on it would outlive what was supposed to stop it.
 */
@ContributesTo(AppScope::class)
@BindingContainer
object AppCoroutineBindings {

    /**
     * The same scope again, unqualified, for the multiplatform classes that take a plain
     * `CoroutineScope` - `@ApplicationScope` is a javax qualifier and cannot appear in commonMain.
     */
    @Provides
    fun unqualifiedAppScope(@ApplicationScope scope: CoroutineScope): CoroutineScope = scope
}
