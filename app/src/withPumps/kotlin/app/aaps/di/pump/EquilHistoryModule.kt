package app.aaps.di.pump

import android.content.Context
import app.aaps.pump.equil.database.EquilHistoryDatabase
import app.aaps.pump.equil.database.EquilHistoryPumpDao
import app.aaps.pump.equil.database.EquilHistoryRecordDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** The Equil history database, provided from `:app` so `:pump:equil` needs no Dagger processor. */
@Module
@InstallIn(SingletonComponent::class)
class EquilHistoryModule {

    @Provides
    @Singleton
    fun provideDatabase(context: Context): EquilHistoryDatabase = EquilHistoryDatabase.build(context)

    @Provides
    @Singleton
    fun provideHistoryRecordDao(equilHistoryDatabase: EquilHistoryDatabase): EquilHistoryRecordDao =
        equilHistoryDatabase.historyRecordDao()

    @Provides
    @Singleton
    fun provideHistoryPumpDao(equilHistoryDatabase: EquilHistoryDatabase): EquilHistoryPumpDao =
        equilHistoryDatabase.historyPumpDao()
}
