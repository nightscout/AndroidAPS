package app.aaps.pump.dana.di

import android.content.Context
import app.aaps.pump.dana.database.DanaHistoryDatabase
import app.aaps.pump.dana.database.DanaHistoryRecordDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class DanaHistoryModule {

    @Provides
    @Singleton
    internal fun provideDatabase(context: Context): DanaHistoryDatabase = DanaHistoryDatabase.build(context)

    @Provides
    @Singleton
    internal fun provideHistoryRecordDao(danaHistoryDatabase: DanaHistoryDatabase): DanaHistoryRecordDao =
        danaHistoryDatabase.historyRecordDao()
}
