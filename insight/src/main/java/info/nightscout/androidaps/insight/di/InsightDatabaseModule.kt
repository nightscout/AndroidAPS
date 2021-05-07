package info.nightscout.androidaps.insight.di

import android.content.Context
import dagger.Module
import dagger.Provides
import info.nightscout.androidaps.plugins.pump.insight.database.InsightDatabase
import info.nightscout.androidaps.plugins.pump.insight.database.InsightDatabaseDao
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


}