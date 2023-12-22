package app.aaps.pump.insight.di

import android.content.Context
import app.aaps.pump.insight.database.InsightDatabase
import app.aaps.pump.insight.database.InsightDatabaseDao
import app.aaps.pump.insight.database.InsightDbHelper
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
class InsightDatabaseModule {

    @Provides
    @Singleton
    internal fun provideDatabase(context: Context): InsightDatabase = InsightDatabase.build(context)

    @Provides
    @Singleton
    internal fun provideInsightDatabaseDao(insightDatabase: InsightDatabase): InsightDatabaseDao =
        insightDatabase.insightDatabaseDao()

    @Provides
    @Singleton
    internal fun provideInsightDbHelper(insightDatabaseDao: InsightDatabaseDao): InsightDbHelper = InsightDbHelper(insightDatabaseDao)

}