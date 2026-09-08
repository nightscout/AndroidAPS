package app.aaps.di.pump

import android.content.Context
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.pump.omnipod.dash.history.DashHistory
import app.aaps.pump.omnipod.dash.history.database.DashHistoryDatabase
import app.aaps.pump.omnipod.dash.history.database.HistoryRecordDao
import app.aaps.pump.omnipod.dash.history.mapper.HistoryMapper
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/** The Dash history database, owned here in `:app`. */
@ContributesTo(AppScope::class)
@BindingContainer
object OmnipodDashHistoryBindings {

    @Provides
    @SingleIn(AppScope::class)
    fun provideDatabase(context: Context): DashHistoryDatabase = DashHistoryDatabase.build(context)

    @Provides
    @SingleIn(AppScope::class)
    fun provideHistoryRecordDao(dashHistoryDatabase: DashHistoryDatabase): HistoryRecordDao =
        dashHistoryDatabase.historyRecordDao()

    // Was @Reusable, which Metro does not support. HistoryMapper holds no state, so @Singleton is the
    // safe replacement: one instance instead of "one or more", never fewer.
    @Provides
    @SingleIn(AppScope::class)
    fun provideHistoryMapper(): HistoryMapper = HistoryMapper()

    @Provides
    @SingleIn(AppScope::class)
    fun provideDashHistory(dao: HistoryRecordDao, historyMapper: HistoryMapper, logger: AAPSLogger): DashHistory =
        DashHistory(dao, historyMapper, logger)
}
