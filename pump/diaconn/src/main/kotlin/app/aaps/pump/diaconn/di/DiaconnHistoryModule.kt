package app.aaps.pump.diaconn.di

import android.content.Context
import dagger.Module
import dagger.Provides
import app.aaps.pump.diaconn.database.DiaconnHistoryDatabase
import app.aaps.pump.diaconn.database.DiaconnHistoryRecordDao
import javax.inject.Singleton

@Module
class DiaconnHistoryModule {

    @Provides
    @Singleton
    internal fun provideDatabase(context: Context): DiaconnHistoryDatabase = DiaconnHistoryDatabase.build(context)

    @Provides
    @Singleton
    internal fun provideHistoryRecordDao(diaconnHistoryDatabase: DiaconnHistoryDatabase): DiaconnHistoryRecordDao =
        diaconnHistoryDatabase.historyRecordDao()
}
