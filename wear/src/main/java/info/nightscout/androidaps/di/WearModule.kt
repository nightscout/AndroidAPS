package info.nightscout.androidaps.di

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Aaps
import info.nightscout.rx.di.RxModule
import info.nightcout.shared.impl.di.SharedImplModule
import info.nightscout.shared.di.SharedModule

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

        @Binds fun bindContext(aaps: Aaps): Context
        @Binds fun bindInjector(aaps: Aaps): HasAndroidInjector
    }
}

