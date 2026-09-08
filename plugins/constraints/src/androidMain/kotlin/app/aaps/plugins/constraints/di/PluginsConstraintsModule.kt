package app.aaps.plugins.constraints.di

import android.content.Context
import app.aaps.core.interfaces.versionChecker.VersionDefinition
import app.aaps.plugins.constraints.signatureVerifier.SignatureVerifierPlugin
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/**
 * What is left of this module's own wiring, now on Metro.
 */
@ContributesTo(AppScope::class)
@BindingContainer
object PluginsConstraintsModule {

    @Provides
    @SingleIn(AppScope::class)
    fun providesVersionDefinition(context: Context, signatureVerifierPlugin: SignatureVerifierPlugin): VersionDefinition =
        VersionDefinition {
            Json.parseToJsonElement(signatureVerifierPlugin.readInputStream(context.assets.open("definition.json"))).jsonObject
        }
}
