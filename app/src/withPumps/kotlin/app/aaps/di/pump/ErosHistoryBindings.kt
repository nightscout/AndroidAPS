package app.aaps.di.pump

import android.content.Context
import app.aaps.pump.omnipod.eros.driver.manager.ErosPodStateManager
import app.aaps.pump.omnipod.eros.history.ErosHistory
import app.aaps.pump.omnipod.eros.history.database.ErosHistoryDatabase
import app.aaps.pump.omnipod.eros.history.database.ErosHistoryRecordDao
import app.aaps.pump.omnipod.eros.manager.AapsErosPodStateManager
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/** The Eros history database and pod state manager. Was `OmnipodErosHistoryModule` + `OmnipodErosModule`. */
@ContributesTo(AppScope::class)
@BindingContainer
object ErosHistoryBindings {

    @Provides @SingleIn(AppScope::class)
    fun provideDatabase(context: Context): ErosHistoryDatabase = ErosHistoryDatabase.build(context)

    @Provides @SingleIn(AppScope::class)
    fun provideHistoryRecordDao(db: ErosHistoryDatabase): ErosHistoryRecordDao = db.historyRecordDao()

    @Provides @SingleIn(AppScope::class)
    fun provideErosHistory(dao: ErosHistoryRecordDao): ErosHistory = ErosHistory(dao)

    @Provides @SingleIn(AppScope::class)
    fun provideErosPodStateManager(impl: AapsErosPodStateManager): ErosPodStateManager = impl
}
