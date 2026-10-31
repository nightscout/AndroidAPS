package app.aaps.core.interfaces.di

/**
 * A dependency that is looked up only when first used.
 * A plain `() -> T` is the obvious deferral, and it is what the kotlin-inject branch uses. Metro
 * cannot: it treats a parameterless function type as its own provider type, so such a parameter is
 * rejected with "may not be intrinsic types". This wrapper is an ordinary class, so Metro accepts it,
 * and unwrapping it inside the graph gives the same late lookup.
 * Temporary, like the bridge itself: when a module's dependencies also live in the new graph, nothing
 * crosses the boundary and none of this is needed.
 */
class DeferredRef<T : Any>(private val supplier: () -> T) {

    fun get(): T = supplier()
}
