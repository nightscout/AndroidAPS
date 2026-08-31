package app.aaps.di.pump

import android.content.Context
import app.aaps.pump.omnipod.eros.history.ErosHistory
import app.aaps.pump.omnipod.eros.history.database.ErosHistoryDatabase
import app.aaps.pump.omnipod.eros.history.database.ErosHistoryRecordDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** The Eros history database, provided from `:app` so `:pump:omnipod:eros` needs no Hilt. */
@Module
@InstallIn(SingletonComponent::class)
class OmnipodErosHistoryModule {

    @Provides
    @Singleton
    fun provideDatabase(context: Context): ErosHistoryDatabase = ErosHistoryDatabase.build(context)

    @Provides
    @Singleton
    fun provideHistoryRecordDao(erosHistoryDatabase: ErosHistoryDatabase): ErosHistoryRecordDao =
        erosHistoryDatabase.historyRecordDao()

    @Provides
    @Singleton
    fun provideErosHistory(dao: ErosHistoryRecordDao): ErosHistory = ErosHistory(dao)
}
