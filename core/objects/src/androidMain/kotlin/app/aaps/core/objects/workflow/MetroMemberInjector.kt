package app.aaps.core.objects.workflow

/**
 * How an Android class that the framework constructs asks Metro to fill its `@Inject` fields.
 *
 * This is the replacement for `dagger.android.HasAndroidInjector`, which answers 294 sites in this
 * tree today. Android builds services, receivers and activities itself, so they cannot get
 * dependencies through a constructor - they have to reach out and ask. The `Application` implements
 * this, and a receiver calls it with `this` exactly where it used to call `AndroidInjection.inject`.
 *
 * The difference from dagger.android is what sits behind it: a class-keyed map of Metro's own
 * `MembersInjector`, built at compile time. dagger.android resolves the same thing reflectively at
 * runtime, which is why a missing entry there is a crash rather than a build failure.
 */
interface MetroMemberInjector {

    /**
     * Fills the `@Inject` fields of [target].
     *
     * Returns false when Metro has no entry for this class, so the caller can fall back to Dagger while
     * both frameworks are present. When Dagger is gone this returns Unit and a miss becomes impossible.
     */
    fun injectMembers(target: Any): Boolean
}
