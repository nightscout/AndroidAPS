package app.aaps.di

import app.aaps.database.di.DatabaseModule
import app.aaps.database.di.TestDatabaseModule
import dagger.Module
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn

/**
 * Replaces the production [DatabaseModule] (real on-disk Room DB) with [TestDatabaseModule]
 * (in-memory Room DB) across the whole instrumented-test Hilt graph. This is the only deliberate
 * deviation from the production DI graph — everything else is inherited from the production
 * `@InstallIn` modules.
 */
@Module(includes = [TestDatabaseModule::class])
@TestInstallIn(components = [SingletonComponent::class], replaces = [DatabaseModule::class])
interface TestDatabaseInstallModule
