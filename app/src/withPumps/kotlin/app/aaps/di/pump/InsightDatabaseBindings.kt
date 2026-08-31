package app.aaps.di.pump

import android.content.Context
import app.aaps.pump.insight.database.InsightDatabase
import app.aaps.pump.insight.database.InsightDatabaseDao
import app.aaps.pump.insight.database.InsightDbHelper
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * The Insight history database, on Metro. Was a Dagger module.
 *
 * `InsightPlugin` is Metro owned now, so Metro has to be able to build what it injects. Nothing on the
 * Dagger side reads these any more.
 */
@ContributesTo(AppScope::class)
@BindingContainer
object InsightDatabaseBindings {

    @Provides @SingleIn(AppScope::class)
    fun provideDatabase(context: Context): InsightDatabase = InsightDatabase.build(context)

    @Provides @SingleIn(AppScope::class)
    fun provideInsightDatabaseDao(insightDatabase: InsightDatabase): InsightDatabaseDao =
        insightDatabase.insightDatabaseDao()

    @Provides @SingleIn(AppScope::class)
    fun provideInsightDbHelper(insightDatabaseDao: InsightDatabaseDao): InsightDbHelper =
        InsightDbHelper(insightDatabaseDao)
}
