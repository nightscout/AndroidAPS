package app.aaps.plugins.sync.di

import app.aaps.plugins.sync.openhumans.OpenHumansWorker
import dagger.Module
import dagger.Provides
import dagger.android.ContributesAndroidInjector
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Suppress("unused")
@Module
@InstallIn(SingletonComponent::class)
abstract class OpenHumansModule {

    // OHLoginActivity is now @AndroidEntryPoint (Hilt-injected)

    @ContributesAndroidInjector abstract fun contributesOpenHumansWorker(): OpenHumansWorker

    companion object {

        @BaseUrl
        @Provides
        fun providesBaseUrl(): String = "https://www.openhumans.org"

        @ClientId
        @Provides
        fun providesClientId(): String = "oie6DvnaEOagTxSoD6BukkLPwDhVr6cMlN74Ihz1"

        @ClientSecret
        @Provides
        fun providesClientSecret(): String =
            "jR0N8pkH1jOwtozHc7CsB1UPcJzFN95ldHcK4VGYIApecr8zGJox0v06xLwPLMASScngT12aIaIHXAVCJeKquEXAWG1XekZdbubSpccgNiQBmuVmIF8nc1xSKSNJltCf"

        @RedirectUrl
        @Provides
        fun providesRedirectUri(): String = "androidaps://setup-openhumans"

        @AuthUrl
        @Provides
        internal fun providesAuthUrl(@ClientId clientId: String): String =
            "https://www.openhumans.org/direct-sharing/projects/oauth2/authorize/?client_id=$clientId&response_type=code"
    }
}
