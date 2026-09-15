package app.aaps.di.pump

import android.content.Context
import app.aaps.pump.equil.database.EquilHistoryDatabase
import app.aaps.pump.equil.database.EquilHistoryPumpDao
import app.aaps.pump.equil.database.EquilHistoryRecordDao
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/** The Equil history database, owned here in `:app`. */
@ContributesTo(AppScope::class)
@BindingContainer
object EquilHistoryBindings {

    @Provides
    @SingleIn(AppScope::class)
    fun provideDatabase(context: Context): EquilHistoryDatabase = EquilHistoryDatabase.build(context)

    @Provides
    @SingleIn(AppScope::class)
    fun provideHistoryRecordDao(equilHistoryDatabase: EquilHistoryDatabase): EquilHistoryRecordDao =
        equilHistoryDatabase.historyRecordDao()

    @Provides
    @SingleIn(AppScope::class)
    fun provideHistoryPumpDao(equilHistoryDatabase: EquilHistoryDatabase): EquilHistoryPumpDao =
        equilHistoryDatabase.historyPumpDao()
}
