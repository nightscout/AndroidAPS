package app.aaps.database.persistence.di

import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.database.persistence.PersistenceLayerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module(
    includes = [
        PersistenceModule.Bindings::class
    ]
)
@InstallIn(SingletonComponent::class)
@Suppress("unused")
abstract class PersistenceModule {

    @Module
    @InstallIn(SingletonComponent::class)
    interface Bindings {

        @Binds fun bindPersistenceLayer(persistenceLayerImpl: PersistenceLayerImpl): PersistenceLayer
    }
}