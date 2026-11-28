package app.aaps.plugins.eversense.di

import android.content.Context
import app.aaps.plugins.eversense.storage.EversenseLegacyStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides

/** Eversense on Android: the shared registration, and the store of the first driver to import once and then delete. */
@ContributesTo(AppScope::class)
@BindingContainer(includes = [EversenseRegistration::class])
object EversenseAndroidRegistration {

    private const val LEGACY_STORE = "EversenseCGMManager"

    @Provides
    fun legacyStore(context: Context): EversenseLegacyStore = object : EversenseLegacyStore {
        override fun read(): Map<String, Any?> = context.getSharedPreferences(LEGACY_STORE, Context.MODE_PRIVATE).all

        // The file goes, not only its values: false when there was none
        override fun delete() {
            context.deleteSharedPreferences(LEGACY_STORE)
        }
    }
}
