package app.aaps.database.persistence.di

import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.database.persistence.PersistenceLayerImpl
import dagger.Binds
import dagger.Module

@Module(
    includes = [
        PersistenceModule.Bindings::class
    ]
)

@Suppress("unused")
abstract class PersistenceModule {

    @Module
    interface Bindings {

        @Binds fun bindPersistenceLayer(persistenceLayerImpl: PersistenceLayerImpl): PersistenceLayer
    }
}