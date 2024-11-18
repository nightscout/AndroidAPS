package app.aaps.wear.di

import app.aaps.wear.WearApp
import dagger.BindsInstance
import dagger.Component
import dagger.android.AndroidInjectionModule
import dagger.android.AndroidInjector
import kotlinx.serialization.InternalSerializationApi
import javax.inject.Singleton

@Singleton
@Component(
    modules = [
        AndroidInjectionModule::class,
        WearModule::class,
        WearServicesModule::class
    ]
)
@InternalSerializationApi
interface WearComponent : AndroidInjector<WearApp> {

    @Component.Builder
    interface Builder {

        @BindsInstance
        fun application(aaps: WearApp): Builder

        fun build(): WearComponent
    }
}