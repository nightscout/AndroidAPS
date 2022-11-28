package info.nightscout.androidaps.di

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.WearApp
import info.nightscout.rx.di.RxModule
import info.nightscout.shared.di.SharedModule
import info.nightscout.shared.impl.di.SharedImplModule

@Suppress("unused")
@Module(
    includes = [
        WearModule.AppBindings::class,
        WearActivitiesModule::class,
        RxModule::class,
        SharedModule::class,
        SharedImplModule::class
    ]
)
open class WearModule {

    @Module
    interface AppBindings {

        @Binds fun bindContext(aaps: WearApp): Context
        @Binds fun bindInjector(aaps: WearApp): HasAndroidInjector
    }
}

