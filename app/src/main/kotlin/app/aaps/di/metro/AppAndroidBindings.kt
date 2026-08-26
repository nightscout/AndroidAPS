package app.aaps.di.metro

import android.content.Context
import android.content.SharedPreferences
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * Android framework objects the graph builds itself, rather than borrowing from Dagger.
 *
 * These have no interface and no implementation class to annotate, so they cannot be contributed with
 * `@ContributesBinding` - they need a container with a `@Provides`. `:app` is Android only, so this can
 * live here; a module that is multiplatform would need the same thing in its **androidMain**, the way
 * `CoreObjectsAndroidContainer` does for `CryptoUtil`.
 *
 * Dagger consumers still get these through the delegates in `CoreObjectsModule`, so there is exactly one
 * instance either way.
 */
@ContributesTo(AppScope::class)
@BindingContainer
object AppAndroidBindings {

    /** Same file and mode as the Dagger provider this replaces - it must be the same preferences file. */
    @Provides
    @SingleIn(AppScope::class)
    fun sharedPreferences(context: Context): SharedPreferences =
        context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)
}
