package app.aaps.plugins.sync.di

import app.aaps.plugins.sync.smsCommunicator.AuthRequest
import dagger.Module
import dagger.android.ContributesAndroidInjector
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
@Suppress("unused")
abstract class SMSCommunicatorModule {

    @ContributesAndroidInjector abstract fun authRequestInjector(): AuthRequest
    // SmsCommunicatorWorker migrated to @HiltWorker (constructed by HiltWorkerFactory).
}
