package app.aaps.pump.omnipod.omnipod5.di

import android.content.Context
import app.aaps.pump.omnipod.omnipod5.history.HistoryMapper
import app.aaps.pump.omnipod.omnipod5.history.O5History
import app.aaps.pump.omnipod.omnipod5.history.database.HistoryRecordDao
import app.aaps.pump.omnipod.omnipod5.history.database.O5HistoryDatabase
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

@ContributesTo(AppScope::class)
@BindingContainer
object O5HistoryBindings {

    @Provides
    @SingleIn(AppScope::class)
    fun provideDatabase(context: Context): O5HistoryDatabase = O5HistoryDatabase.build(context)

    @Provides
    @SingleIn(AppScope::class)
    fun provideDao(database: O5HistoryDatabase): HistoryRecordDao = database.historyRecordDao()

    @Provides
    @SingleIn(AppScope::class)
    fun provideMapper(): HistoryMapper = HistoryMapper()

    @Provides
    @SingleIn(AppScope::class)
    fun provideHistory(dao: HistoryRecordDao, mapper: HistoryMapper): O5History = O5History(dao, mapper)
}
