package info.nightscout.androidaps.plugins.pump.omnipod.dash.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.Reusable
import info.nightscout.androidaps.plugins.pump.omnipod.dash.history.DashHistory
import info.nightscout.androidaps.plugins.pump.omnipod.dash.history.database.DashHistoryDatabase
import info.nightscout.androidaps.plugins.pump.omnipod.dash.history.database.HistoryRecordDao
import info.nightscout.androidaps.plugins.pump.omnipod.dash.history.mapper.HistoryMapper
import info.nightscout.rx.logging.AAPSLogger
import javax.inject.Singleton

@Module
class OmnipodDashHistoryModule {

    @Provides
    @Singleton
    internal fun provideDatabase(context: Context): DashHistoryDatabase = DashHistoryDatabase.build(context)

    @Provides
    @Singleton
    internal fun provideHistoryRecordDao(dashHistoryDatabase: DashHistoryDatabase): HistoryRecordDao =
        dashHistoryDatabase.historyRecordDao()

    @Provides
    @Reusable // no state, let system decide when to reuse or create new.
    internal fun provideHistoryMapper() = HistoryMapper()

    @Provides
    @Singleton
    internal fun provideDashHistory(dao: HistoryRecordDao, historyMapper: HistoryMapper, logger: AAPSLogger) =
        DashHistory(dao, historyMapper, logger)
}
