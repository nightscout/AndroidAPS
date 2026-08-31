package app.aaps.di.metro

import android.content.Context
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
 * Use [configure] to stub the few leaves a test really needs:
 *
 * ```kotlin
 * val root = testRoot { whenever(it.aapsLogger()).thenReturn(aapsLogger) }
 * ```
 *
 * This is the only way to reach a feature graph from a test now. They are `@GraphExtension`s, so they
 * cannot be created on their own - which is the point of the restructure, and is why the constraints
 * and source graph tests live in this module rather than beside their graphs.
 */
fun testRoot(configure: (AapsLeaves) -> Unit = {}): AppRootGraph {
    // RETURNS_MOCKS, not the default: an unstubbed @Provides would otherwise hand back null, and the
    // failure lands far away - inside a plugin constructor, as "parameter aapsLogger is null".
    val leaves = mock<AapsLeaves>(defaultAnswer = Answers.RETURNS_MOCKS)
    // A real scope, because a mocked one is not merely inert here. Plugins that Metro builds now touch it
    // while being constructed - `appScope.coroutineContext[Job]` - and a mock returns a mock Element,
    // which fails as `ClassCastException ... cannot be cast to Job` from inside the graph. Unconfined so
    // anything launched runs on the calling thread and no test has to wait for it.
    val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
    configure(leaves)
    // PumpLeaves is mocked like AapsLeaves: the test source set compiles against the `full` flavour, so
    // this is the pump-bearing copy, and no test needs a real BLE transport.
    return createGraphFactory<AppRootGraph.Factory>()
        .create(scope, mock<Context>(defaultAnswer = Answers.RETURNS_MOCKS), leaves, CoreObjectsGraph)
}
