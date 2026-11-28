package app.aaps.plugins.eversense.di

import app.aaps.plugins.eversense.storage.EversenseLegacyStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides

/** Eversense on iOS: the shared registration. There was never a first driver on iOS, so nothing to import. */
@ContributesTo(AppScope::class)
@BindingContainer(includes = [EversenseRegistration::class])
object EversenseIosRegistration {

    @Provides
    fun legacyStore(): EversenseLegacyStore = object : EversenseLegacyStore {
        override fun read(): Map<String, Any?> = emptyMap()

        // Nothing was ever stored there on iOS
        override fun delete() = Unit
    }
}
