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
 * The Insight pump's own database, provided to the graph.
 *
 * Here rather than in the pump module because it is built from the application `Context`, and in the
 * `withPumps` source set so a follower build - which has no pump drivers on its classpath - does not
 * carry it.
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
