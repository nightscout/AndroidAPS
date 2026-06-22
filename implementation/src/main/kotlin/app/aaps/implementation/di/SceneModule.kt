package app.aaps.implementation.di

import app.aaps.core.interfaces.scenes.ActiveSceneSync
import app.aaps.core.interfaces.scenes.SceneActions
import app.aaps.core.interfaces.scenes.SceneAutomationApi
import app.aaps.core.interfaces.scenes.SceneChainResolver
import app.aaps.core.interfaces.scenes.SceneStore
import app.aaps.core.interfaces.scenes.Scenes
import app.aaps.implementation.scenes.ActiveSceneManager
import app.aaps.implementation.scenes.SceneActionsImpl
import app.aaps.implementation.scenes.SceneAutomationApiImpl
import app.aaps.implementation.scenes.SceneChainTargetResolver
import app.aaps.implementation.scenes.SceneRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Bindings for the scene engine, which lives in `:implementation` (the execution layer). Icon
 * resolution ([app.aaps.core.interfaces.scenes.SceneIconResolver]) stays bound in `:ui` since the
 * icon catalog is a UI concern.
 */
@Module
@InstallIn(SingletonComponent::class)
interface SceneModule {

    @Binds fun bindSceneAutomationApi(impl: SceneAutomationApiImpl): SceneAutomationApi

    @Binds fun bindSceneActions(impl: SceneActionsImpl): SceneActions

    @Binds fun bindScenes(impl: SceneRepository): Scenes

    @Binds fun bindSceneStore(impl: SceneRepository): SceneStore

    @Binds fun bindSceneChainResolver(impl: SceneChainTargetResolver): SceneChainResolver

    @Binds fun bindActiveSceneSync(impl: ActiveSceneManager): ActiveSceneSync
}
