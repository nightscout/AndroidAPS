package info.nightscout.pump.dana.di

import android.content.Context
import dagger.Module
import dagger.Provides
import info.nightscout.pump.dana.database.DanaHistoryDatabase
import info.nightscout.pump.dana.database.DanaHistoryRecordDao
import javax.inject.Singleton

@Module
class DanaHistoryModule {

    @Provides
    @Singleton
    internal fun provideDatabase(context: Context): DanaHistoryDatabase = DanaHistoryDatabase.build(context)

    @Provides
    @Singleton
    internal fun provideHistoryRecordDao(danaHistoryDatabase: DanaHistoryDatabase): DanaHistoryRecordDao =
        danaHistoryDatabase.historyRecordDao()
}
