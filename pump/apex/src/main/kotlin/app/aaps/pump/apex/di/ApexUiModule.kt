package app.aaps.pump.apex.di

import app.aaps.pump.apex.ui.ApexFragment
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
@Suppress("unused")
abstract class ApexUiModule {
    @ContributesAndroidInjector abstract fun contributesApexFragment(): ApexFragment
}
