package info.nightscout.androidaps.plugins.pump.omnipod.eros.di

import android.content.Context
import dagger.Module
import dagger.Provides
import info.nightscout.androidaps.plugins.pump.omnipod.eros.history.ErosHistory
import info.nightscout.androidaps.plugins.pump.omnipod.eros.history.database.ErosHistoryDatabase
import info.nightscout.androidaps.plugins.pump.omnipod.eros.history.database.ErosHistoryRecordDao
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
