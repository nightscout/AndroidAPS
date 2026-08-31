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
 * There is nothing to stub: the graph builds everything.
 * demand, and Metro only calls the ones the graph under test actually reaches - so a test says what it
 * cares about and nothing else.
 * This is the only way to reach a feature graph from a test now. They are `@GraphExtension`s, so they
 * cannot be created on their own - which is the point of the restructure, and is why the constraints
 * and source graph tests live in this module rather than beside their graphs.
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
