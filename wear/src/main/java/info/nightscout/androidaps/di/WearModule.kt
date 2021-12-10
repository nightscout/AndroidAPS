package info.nightscout.androidaps.di

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Aaps

@Suppress("unused")
@Module(includes = [
    WearModule.AppBindings::class
])
open class WearModule {

    @Module
    interface AppBindings {

        @Binds fun bindContext(aaps: Aaps): Context
        @Binds fun bindInjector(aaps: Aaps): HasAndroidInjector
    }
}

