package info.nightscout.androidaps.dana.di

import android.content.Context
import dagger.Module
import dagger.Provides
import info.nightscout.androidaps.dana.database.DanaHistoryDatabase
import info.nightscout.androidaps.dana.database.DanaHistoryRecordDao
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
