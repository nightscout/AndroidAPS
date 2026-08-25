package app.aaps.plugins.constraints.di

import android.content.Context
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.versionChecker.VersionCheckerUtils
import app.aaps.core.interfaces.versionChecker.VersionDefinition
import app.aaps.plugins.constraints.ConstraintsCheckerImpl
import app.aaps.plugins.constraints.signatureVerifier.SignatureVerifierPlugin
import app.aaps.plugins.constraints.versionChecker.VersionCheckerUtilsImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import javax.inject.Singleton

@Module(
    includes = [
        PluginsConstraintsModule.Bindings::class,
    ]
)
@InstallIn(SingletonComponent::class)
@Suppress("unused")
open class PluginsConstraintsModule {

    @Module
    @InstallIn(SingletonComponent::class)
    interface Bindings {

        @Binds fun bindVersionCheckerUtils(versionCheckerUtils: VersionCheckerUtilsImpl): VersionCheckerUtils
        // BgQualityCheck, DstHelper and Objectives are NOT bound here any more. Metro builds those
        // plugins now, so a @Binds would have Dagger construct a second copy: the plugin list would
        // hold the started one and these interfaces would hand out an unstarted twin. See the
        // @Provides delegates below.
        @Binds fun bindsConstraintChecker(constraintsCheckerImpl: ConstraintsCheckerImpl): ConstraintsChecker
    }

    // The BgQualityCheck, DstHelper and Objectives delegates moved to `MetroBridgeModule` in `:app`.
    // The graph is a `@GraphExtension` now, so it can only be opened from the root graph, and the root
    // graph lives there. See that file for the duplicate-instance hazard these delegates prevent.

    @Provides
    @Singleton
    fun providesVersionDefinition(context: Context, signatureVerifierPlugin: SignatureVerifierPlugin): VersionDefinition = VersionDefinition { Json.parseToJsonElement(signatureVerifierPlugin.readInputStream(context.assets.open("definition.json"))).jsonObject }
}