package app.aaps.pump.dana.di

import android.content.Context
import app.aaps.pump.dana.database.DanaHistoryDatabase
import app.aaps.pump.dana.database.DanaHistoryRecordDao
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * The Dana history database.
 *
 * Lives in this module rather than in the app's `withPumps` source set, where it used to be. Nothing
 * here needs the app - it only provides types this module owns - and keeping it here means the app
 * names no Dana type, so removing `:pump:dana` from `settings.gradle` cannot break the app's build.
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
