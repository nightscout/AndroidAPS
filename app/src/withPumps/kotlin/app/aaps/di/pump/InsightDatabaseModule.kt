package app.aaps.di.pump

import android.content.Context
import app.aaps.pump.insight.database.InsightDatabase
import app.aaps.pump.insight.database.InsightDatabaseDao
import app.aaps.pump.insight.database.InsightDbHelper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** The Insight history database, provided from `:app` so `:pump:insight` needs no Dagger processor. */
@Module
@InstallIn(SingletonComponent::class)
class InsightDatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(context: Context): InsightDatabase = InsightDatabase.build(context)

    @Provides
    @Singleton
    fun provideInsightDatabaseDao(insightDatabase: InsightDatabase): InsightDatabaseDao =
        insightDatabase.insightDatabaseDao()

    @Provides
    @Singleton
    fun provideInsightDbHelper(insightDatabaseDao: InsightDatabaseDao): InsightDbHelper = InsightDbHelper(insightDatabaseDao)
}
