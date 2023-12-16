package app.aaps.pump.dana.di

import android.content.Context
import dagger.Module
import dagger.Provides
import app.aaps.pump.dana.database.DanaHistoryDatabase
import app.aaps.pump.dana.database.DanaHistoryRecordDao
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
