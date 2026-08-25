package app.aaps.di.metro

import app.aaps.implementation.scenes.ActiveSceneManager
import com.google.common.truth.Truth.assertThat
import dev.zacsweers.metro.SingleIn
import org.junit.jupiter.api.Test

/**
 * Nothing Metro already owns is handed back in through [AapsLeaves].
 *
 * [AapsLeaves] points one way: Dagger builds a thing, Metro receives it. Listing a class that Metro owns
 * - one carrying `@SingleIn` - reverses that, and the result is not a duplicate binding the compiler can
 * catch. It is two live objects. Worse, a Metro-owned class usually has no javax scope at all, so
 * Dagger's side is *unscoped*: every injection point gets a fresh one.
 *
 * `ActiveSceneManager` was exactly this. `SceneExecutor` is a Dagger `@Singleton` that asks for the class,
 * so it wrote the active scene into an object of its own, while `SceneListViewModel` and the overview
 * banner read Metro's through `ActiveSceneSync`. Activating a scene changed nothing on screen, and the
 * setup-wizard e2e test failed on `assertVisible("End Scene")` with nothing in any log to explain it.
 * `SceneRepository` had the same backwards leaf.
 *
 * The rule this fixes on: when Metro owns a class, Dagger gets it from `MetroGraphs` through a `@Provides`
 * in `CoreObjectsModule` - never the other way round.
 *
 * This is the mirror of `PumpLeavesTest`, which guards the opposite mistake: a javax `@Singleton` that
 * Metro builds a second copy of because nothing hands Dagger's over. Neither variant fails the build, and
 * both only ever show up as a screen that does not react.
 */
class LeafOwnershipTest {

    @Test
    fun `no Metro-owned class is offered by AapsLeaves`() {
        val offenders = AapsLeaves::class.java.declaredMethods
            .map { it.returnType }
            .filter { it.isAnnotationPresent(SingleIn::class.java) }
            .map { it.simpleName }
            .distinct()

        assertThat(offenders).isEmpty()
    }

    @Test
    fun `the check can actually see a Metro scope at runtime`() {
        // Guards the guard. `@SingleIn` is only visible to `isAnnotationPresent` if it is kept at runtime;
        // if that ever stops being true the test above would pass by finding nothing, which reads exactly
        // like success. ActiveSceneManager is the class the rule was written for.
        assertThat(ActiveSceneManager::class.java.isAnnotationPresent(SingleIn::class.java)).isTrue()
    }
}
