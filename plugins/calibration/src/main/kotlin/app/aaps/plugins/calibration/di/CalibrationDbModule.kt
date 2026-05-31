package app.aaps.plugins.calibration.di

import android.content.Context
import app.aaps.plugins.calibration.db.CalibrationDatabase
import app.aaps.plugins.calibration.db.CalibrationEntryDao
import app.aaps.plugins.calibration.db.CalibrationRepository
import app.aaps.plugins.calibration.db.CalibrationRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class CalibrationDbModule {

    @Provides
    @Singleton
    internal fun provideDatabase(context: Context): CalibrationDatabase = CalibrationDatabase.build(context)

    @Provides
    @Singleton
    internal fun provideDao(database: CalibrationDatabase): CalibrationEntryDao = database.calibrationEntryDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class CalibrationRepositoryModule {

    @Binds
    @Singleton
    internal abstract fun bindRepository(impl: CalibrationRepositoryImpl): CalibrationRepository
}
