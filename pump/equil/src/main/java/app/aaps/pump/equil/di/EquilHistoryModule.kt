package app.aaps.pump.equil.di

import android.content.Context
import app.aaps.pump.equil.database.EquilHistoryDatabase
import app.aaps.pump.equil.database.EquilHistoryPumpDao
import app.aaps.pump.equil.database.EquilHistoryRecordDao
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
@Suppress("unused")
class EquilHistoryModule {

    @Provides
    @Singleton
    internal fun provideDatabase(context: Context): EquilHistoryDatabase = EquilHistoryDatabase.build(context)

    @Provides
    @Singleton
    internal fun provideHistoryRecordDao(equilHistoryDatabase: EquilHistoryDatabase): EquilHistoryRecordDao =
        equilHistoryDatabase.historyRecordDao()

    @Provides
    @Singleton
    internal fun provideHistoryPumpDao(equilHistoryDatabase: EquilHistoryDatabase): EquilHistoryPumpDao =
        equilHistoryDatabase.historyPumpDao()
}
