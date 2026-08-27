package app.aaps.di

import app.aaps.database.di.DatabaseConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

/**
 * Replaces the production database (a real file on disk) with the in-memory copy for the whole
 * instrumented-test graph. This is the only deliberate deviation from the production DI graph -
 * everything else is inherited from the production modules.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DatabaseConfigModule::class])
class TestDatabaseConfigModule {

    @Provides
    @Singleton
    fun provideDatabaseConfig(): DatabaseConfig = DatabaseConfig.IN_MEMORY
}
