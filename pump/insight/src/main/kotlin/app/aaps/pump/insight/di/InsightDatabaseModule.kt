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
 * The Insight pump's own database, on Metro.
 *
 * It moved because `InsightPlugin` did: the plugin registers itself into the Metro plugin map now, so
 * the graph has to be able to build it, and it takes both [InsightDatabase] and [InsightDbHelper].
 * Nothing else in the tree injects these three - only the plugin - so there is no Dagger delegate to
 * write and no chance of the two frameworks each opening the database.
 *
 * Scoped with `@SingleIn`, not javax `@Singleton`: the graph that reads this is generated in `:app` and
 * has no Dagger interop, so a javax scope would be ignored and every read would open a second Room
 * database over the same file.
 */
@ContributesTo(AppScope::class)
@BindingContainer
object InsightDatabaseModule {

    @Provides
    @SingleIn(AppScope::class)
    fun database(context: Context): InsightDatabase = InsightDatabase.build(context)

    @Provides
    @SingleIn(AppScope::class)
    fun insightDatabaseDao(insightDatabase: InsightDatabase): InsightDatabaseDao =
        insightDatabase.insightDatabaseDao()

    @Provides
    @SingleIn(AppScope::class)
    fun insightDbHelper(insightDatabaseDao: InsightDatabaseDao): InsightDbHelper = InsightDbHelper(insightDatabaseDao)
}
