package app.aaps.pump.aaruy.di

import android.content.Context
import dagger.Module
import dagger.Provides
import app.aaps.pump.aaruy.database.AaruyHistoryDatabase
import app.aaps.pump.aaruy.database.HistoryDailyUnitInfoDao
import app.aaps.pump.aaruy.database.HistoryUnitInfoDao
import javax.inject.Singleton

@Module
class AaruyHistoryModule {
    @Provides
    @Singleton
    internal fun provideDatabase(context: Context): AaruyHistoryDatabase = AaruyHistoryDatabase.build(context)

    @Provides
    @Singleton
    internal fun provideHistoryDailyUnitInfoDao(aaruyHistoryDatabase: AaruyHistoryDatabase): HistoryDailyUnitInfoDao =
        aaruyHistoryDatabase.historyDailyUnitInfoDao()

    @Provides
    @Singleton
    internal fun provideHistoryUnitInfoDao(aaruyHistoryDatabase: AaruyHistoryDatabase): HistoryUnitInfoDao =
        aaruyHistoryDatabase.historyUnitInfoDao()
}