package app.aaps.plugins.eversense.di

import android.content.Context
import app.aaps.plugins.eversense.storage.EversenseLegacyStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides

/** Eversense on Android: the shared registration, and the store of the first driver to import once. */
@ContributesTo(AppScope::class)
@BindingContainer(includes = [EversenseRegistration::class])
object EversenseAndroidRegistration {

    @Provides
    fun legacyStore(context: Context): EversenseLegacyStore = EversenseLegacyStore {
        context.getSharedPreferences("EversenseCGMManager", Context.MODE_PRIVATE).all
    }
}
