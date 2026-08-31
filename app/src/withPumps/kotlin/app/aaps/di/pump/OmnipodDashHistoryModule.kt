package app.aaps.di.pump

import android.content.Context
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.pump.omnipod.dash.history.DashHistory
import app.aaps.pump.omnipod.dash.history.database.DashHistoryDatabase
import app.aaps.pump.omnipod.dash.history.database.HistoryRecordDao
import app.aaps.pump.omnipod.dash.history.mapper.HistoryMapper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** The Dash history database, provided from `:app` so `:pump:omnipod:dash` needs no Dagger processor. */
@Module
@InstallIn(SingletonComponent::class)
class OmnipodDashHistoryModule {

    @Provides
    @Singleton
    fun provideDatabase(context: Context): DashHistoryDatabase = DashHistoryDatabase.build(context)

    @Provides
    @Singleton
    fun provideHistoryRecordDao(dashHistoryDatabase: DashHistoryDatabase): HistoryRecordDao =
        dashHistoryDatabase.historyRecordDao()

    // Was @Reusable, which Metro does not support. HistoryMapper holds no state, so @Singleton is the
    // safe replacement: one instance instead of "one or more", never fewer.
    @Provides
    @Singleton
    fun provideHistoryMapper(): HistoryMapper = HistoryMapper()

    @Provides
    @Singleton
    fun provideDashHistory(dao: HistoryRecordDao, historyMapper: HistoryMapper, logger: AAPSLogger): DashHistory =
        DashHistory(dao, historyMapper, logger)
}
