package info.nightscout.androidaps.dependencyInjection

import dagger.Component
import dagger.android.AndroidInjectionModule
import info.nightscout.androidaps.MainApp


@Component(
        modules = [
            AndroidInjectionModule::class,
            ActivitiesModule::class,
            AppModule::class
        ]
)

interface AppComponent {
    fun inject(mainApp: MainApp)
}