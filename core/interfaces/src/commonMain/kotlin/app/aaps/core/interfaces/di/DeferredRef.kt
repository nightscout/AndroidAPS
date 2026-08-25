package app.aaps.core.interfaces.di

/**
 * A dependency that is looked up only when first used.
 *
 * Exists because of one specific problem. While Dagger and a multiplatform DI framework coexist, the
 * bridge makes them mutually reachable: asking the new graph for an object resolves its dependencies
 * from Dagger, and Dagger reaches back - `Loop` leads to the plugin list, which asks the new graph.
 * Neither framework can see the other's graph, so neither can detect the cycle, and it surfaces as a
 * StackOverflowError on device. It did exactly that on all three spike branches.
 *
 * A plain `() -> T` is the obvious deferral, and it is what the kotlin-inject branch uses. Metro
 * cannot: it treats a parameterless function type as its own provider type, so such a parameter is
 * rejected with "may not be intrinsic types". This wrapper is an ordinary class, so Metro accepts it,
 * and unwrapping it inside the graph gives the same late lookup.
 *
 * Temporary, like the bridge itself: when a module's dependencies also live in the new graph, nothing
 * crosses the boundary and none of this is needed.
 */
class DeferredRef<T : Any>(private val supplier: () -> T) {

    fun get(): T = supplier()
}
