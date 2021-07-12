package info.nightscout.androidaps.diaconn.di

import android.content.Context
import dagger.Module
import dagger.Provides
import info.nightscout.androidaps.diaconn.database.DiaconnHistoryDatabase
import info.nightscout.androidaps.diaconn.database.DiaconnHistoryRecordDao
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
