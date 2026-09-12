package app.aaps.di.pump

import android.content.Context
import app.aaps.pump.dana.database.DanaHistoryDatabase
import app.aaps.pump.dana.database.DanaHistoryRecordDao
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * The Dana history database, provided from `:app` rather than from `:pump:dana`.
 */
@ContributesTo(AppScope::class)
@BindingContainer
object DanaHistoryBindings {

    @Provides
    @SingleIn(AppScope::class)
    fun provideDatabase(context: Context): DanaHistoryDatabase = DanaHistoryDatabase.build(context)

    @Provides
    @SingleIn(AppScope::class)
    fun provideHistoryRecordDao(danaHistoryDatabase: DanaHistoryDatabase): DanaHistoryRecordDao =
        danaHistoryDatabase.historyRecordDao()
}
