package app.aaps.di.metro

import app.aaps.core.interfaces.di.ApplicationScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import kotlinx.coroutines.CoroutineScope

/**
 * The application scope, owned by Metro.
 * There must be exactly one. A second scope would not fail anything loudly: it would simply never be
 * cancelled with the first, so work started on it would outlive what was supposed to stop it.
 */
@ContributesTo(AppScope::class)
@BindingContainer
object AppCoroutineBindings {

    /**
     * The same scope again, unqualified, for the classes that take a plain `CoroutineScope`.
     *
     * Not because the qualifier is unavailable to them: `ApplicationScope` is a Metro `@Qualifier`
     * and lives in `core/interfaces` **commonMain**, so shared code can and does ask for it -
     * `ClientGraphBindings` does exactly that. The comment here used to say it was a javax qualifier
     * that could not appear in commonMain, which was wrong on both counts.
     *
     * The alias exists so that a class which only ever wants "the app scope" can say so without
     * naming a qualifier. The client graphs derive the qualified one from the plain one instead -
     * opposite order, same single object, and each graph owns whichever end it declares.
     */
    @Provides
    fun unqualifiedAppScope(@ApplicationScope scope: CoroutineScope): CoroutineScope = scope
}
