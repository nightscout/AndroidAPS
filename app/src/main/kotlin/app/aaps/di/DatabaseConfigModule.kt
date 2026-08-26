package app.aaps.di

import app.aaps.database.di.DatabaseConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Which database the app opens. Its own module so the instrumented tests can replace exactly this
 * one binding with `@TestInstallIn` and get the in-memory copy - see `TestDatabaseConfigModule`.
 */
@Module
@InstallIn(SingletonComponent::class)
@Suppress("unused")
class DatabaseConfigModule {

    @Provides
    @Singleton
    fun provideDatabaseConfig(): DatabaseConfig = DatabaseConfig.PRODUCTION
}
