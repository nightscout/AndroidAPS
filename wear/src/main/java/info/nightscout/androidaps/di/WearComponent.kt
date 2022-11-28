package info.nightscout.androidaps.di

import dagger.BindsInstance
import dagger.Component
import dagger.android.AndroidInjectionModule
import dagger.android.AndroidInjector
import info.nightscout.androidaps.WearApp
import javax.inject.Singleton

@Singleton
@Component(
    modules = [
        AndroidInjectionModule::class,
        WearModule::class,
        WearServicesModule::class
    ]
)
interface WearComponent : AndroidInjector<WearApp> {

    @Component.Builder
    interface Builder {

        @BindsInstance
        fun application(aaps: WearApp): Builder

        fun build(): WearComponent
    }
}