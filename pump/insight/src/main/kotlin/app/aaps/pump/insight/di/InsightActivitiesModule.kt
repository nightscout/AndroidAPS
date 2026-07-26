package app.aaps.pump.insight.di

import app.aaps.pump.insight.app_layer.activities.InsightAlertActivity
import dagger.Module
import dagger.android.ContributesAndroidInjector
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
@Suppress("unused")
abstract class InsightActivitiesModule {

    @ContributesAndroidInjector abstract fun contributesInsightAlertActivity(): InsightAlertActivity
}