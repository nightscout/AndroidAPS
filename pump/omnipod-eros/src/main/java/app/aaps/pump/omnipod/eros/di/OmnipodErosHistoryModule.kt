package app.aaps.pump.omnipod.eros.di

import android.content.Context
import app.aaps.pump.omnipod.eros.history.ErosHistory
import app.aaps.pump.omnipod.eros.history.database.ErosHistoryDatabase
import app.aaps.pump.omnipod.eros.history.database.ErosHistoryRecordDao
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
class OmnipodErosHistoryModule {

    @Provides
    @Singleton
    internal fun provideDatabase(context: Context): ErosHistoryDatabase = ErosHistoryDatabase.build(context)

    @Provides
    @Singleton
    internal fun provideHistoryRecordDao(erosHistoryDatabase: ErosHistoryDatabase): ErosHistoryRecordDao =
        erosHistoryDatabase.historyRecordDao()

    @Provides
    @Singleton
    internal fun provideErosHistory(dao: ErosHistoryRecordDao) =
        ErosHistory(dao)

}
