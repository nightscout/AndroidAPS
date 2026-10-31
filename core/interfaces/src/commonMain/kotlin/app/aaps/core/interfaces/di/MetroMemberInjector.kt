package app.aaps.core.interfaces.di

/**
 * How an Android class that the framework constructs asks Metro to fill its `@Inject` fields.
 */
// A `fun interface` so a test can supply one as a lambda, the way `HasAndroidInjector` allowed.
fun interface MetroMemberInjector {

    /**
     * Fills the `@Inject` fields of [target].
     */
    fun injectMembers(target: Any): Boolean
}
