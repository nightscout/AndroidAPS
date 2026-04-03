package app.aaps.pump.diaconn.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import app.aaps.pump.diaconn.database.DiaconnHistoryDatabase
import app.aaps.pump.diaconn.database.DiaconnHistoryRecordDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class DiaconnHistoryModule {

    @Provides
    @Singleton
    internal fun provideDatabase(context: Context): DiaconnHistoryDatabase = DiaconnHistoryDatabase.build(context)

    @Provides
    @Singleton
    internal fun provideHistoryRecordDao(diaconnHistoryDatabase: DiaconnHistoryDatabase): DiaconnHistoryRecordDao =
        diaconnHistoryDatabase.historyRecordDao()
}
