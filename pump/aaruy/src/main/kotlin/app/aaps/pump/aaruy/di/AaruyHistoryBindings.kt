package app.aaps.pump.aaruy.di

import android.content.Context
import app.aaps.pump.aaruy.database.AaruyHistoryDatabase
import app.aaps.pump.aaruy.database.HistoryDailyUnitInfoDao
import app.aaps.pump.aaruy.database.HistoryUnitInfoDao
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/** The Diaconn history database, owned here in `:app`. */
@ContributesTo(AppScope::class)
@BindingContainer
object AaruyHistoryBindings {

    @Provides
    @SingleIn(AppScope::class)
    fun provideDatabase(context: Context): AaruyHistoryDatabase = AaruyHistoryDatabase.build(context)

    @Provides
    @SingleIn(AppScope::class)
    fun provideHistoryRecordDao(aaruyHistoryDatabase: AaruyHistoryDatabase): HistoryUnitInfoDao =
        aaruyHistoryDatabase.historyUnitInfoDao()

    @Provides
    @SingleIn(AppScope::class)
    fun provideHistoryDailyRecordDao(aaruyHistoryDailyDatabase: AaruyHistoryDatabase): HistoryDailyUnitInfoDao =
        aaruyHistoryDailyDatabase.historyDailyUnitInfoDao()
}
