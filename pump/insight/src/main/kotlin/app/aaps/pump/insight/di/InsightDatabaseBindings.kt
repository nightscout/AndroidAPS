package app.aaps.pump.insight.di

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
 * The Insight pump's own database, provided to the graph. It is built from the application `Context`,
 * which the graph provides, so it can live in the pump module and a follower build - which has no pump
 * drivers on its classpath - does not carry it.
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
