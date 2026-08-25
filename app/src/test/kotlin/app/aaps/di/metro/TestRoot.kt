package app.aaps.di.metro

import app.aaps.core.objects.di.CoreObjectsGraph
import dev.zacsweers.metro.createGraphFactory
import org.mockito.Answers
import org.mockito.kotlin.mock

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
    configure(leaves)
    // PumpLeaves is mocked like AapsLeaves: the test source set compiles against the `full` flavour, so
    // this is the pump-bearing copy, and no test needs a real BLE transport.
    return createGraphFactory<AppRootGraph.Factory>()
        .create(leaves, CoreObjectsGraph, mock<PumpLeaves>(defaultAnswer = Answers.RETURNS_MOCKS))
}
