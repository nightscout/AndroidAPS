package app.aaps.plugins.constraints.di

import android.content.Context
import app.aaps.core.interfaces.bgQualityCheck.BgQualityCheck
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.constraints.Objectives
import app.aaps.core.interfaces.versionChecker.VersionCheckerUtils
import app.aaps.core.interfaces.versionChecker.VersionDefinition
import app.aaps.plugins.constraints.ConstraintsCheckerImpl
import app.aaps.plugins.constraints.bgQualityCheck.BgQualityCheckPlugin
import app.aaps.plugins.constraints.objectives.ObjectivesPlugin
import app.aaps.plugins.constraints.signatureVerifier.SignatureVerifierPlugin
import app.aaps.plugins.constraints.versionChecker.VersionCheckerUtilsImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import org.json.JSONObject
import javax.inject.Singleton

@Module(
    includes = [
        PluginsConstraintsModule.Bindings::class,
        ObjectivesModule::class
    ]
)

@Suppress("unused")
open class PluginsConstraintsModule {

    @Module
    interface Bindings {

        @Binds fun bindVersionCheckerUtils(versionCheckerUtils: VersionCheckerUtilsImpl): VersionCheckerUtils
        @Binds fun bindBgQualityCheck(bgQualityCheck: BgQualityCheckPlugin): BgQualityCheck
        @Binds fun bindsConstraintChecker(constraintsCheckerImpl: ConstraintsCheckerImpl): ConstraintsChecker
        @Binds fun bindsObjectives(objectivesPlugin: ObjectivesPlugin): Objectives
    }

    @Provides
    @Singleton
    fun providesVersionDefinition(context: Context, signatureVerifierPlugin: SignatureVerifierPlugin): VersionDefinition = VersionDefinition { JSONObject(signatureVerifierPlugin.readInputStream(context.assets.open("definition.json"))) }
}