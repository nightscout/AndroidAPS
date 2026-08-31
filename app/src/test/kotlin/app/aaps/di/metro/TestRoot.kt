package app.aaps.di.metro

import android.content.Context
import app.aaps.core.interfaces.di.MetroMemberInjector
import app.aaps.database.di.DatabaseConfig
import app.aaps.di.ExternalOptionsOverride
import app.aaps.core.objects.di.CoreObjectsGraph
import dev.zacsweers.metro.createGraphFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.mockito.Answers
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Builds a real [AppRootGraph] for tests, with everything Dagger owns mocked.
 *
 * [AapsLeaves] carries about sixty leaves now, so listing them in every test would be worse than the
 * positional argument lists this replaced. A mock of the container answers each `@Provides` function on
 * demand, and Metro only calls the ones the graph under test actually reaches - so a test says what it
 * cares about and nothing else.
 *
 * This is the only way to reach a feature graph from a test now. They are `@GraphExtension`s, so they
 * cannot be created on their own - which is the point of the restructure, and is why the constraints
 * and source graph tests live in this module rather than beside their graphs.
 *
 * There is nothing left to stub. This used to take a `configure: (AapsLeaves) -> Unit` so a test could
 * stub the handful of objects Dagger still owned, mocked with `RETURNS_MOCKS` because an unstubbed
 * `@Provides` handing back null failed far away - inside a plugin constructor, as "parameter aapsLogger
 * is null". `AapsLeaves` is gone: Metro builds everything, so the graph builds it here too.
 */
fun testRoot(): AppRootGraph {
    // A real scope, because a mocked one is not merely inert here. Plugins that Metro builds now touch it
    // while being constructed - `appScope.coroutineContext[Job]` - and a mock returns a mock Element,
    // which fails as `ClassCastException ... cannot be cast to Job` from inside the graph. Unconfined so
    // anything launched runs on the calling thread and no test has to wait for it.
    val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
    return createGraphFactory<AppRootGraph.Factory>().create(
        scope,
        mock<Context>(defaultAnswer = Answers.RETURNS_MOCKS),
        // Nothing in a plain-JVM graph test injects members or opens the database, and no test wants a
        // forced external option - these are the inert values for all three.
        mock<MetroMemberInjector>(defaultAnswer = Answers.RETURNS_MOCKS),
        DatabaseConfig.IN_MEMORY,
        ExternalOptionsOverride.NONE,
        CoreObjectsGraph
    )
}
