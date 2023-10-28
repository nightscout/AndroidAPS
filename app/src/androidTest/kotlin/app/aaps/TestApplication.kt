package app.aaps

import app.aaps.di.DaggerTestAppComponent
import dagger.android.AndroidInjector
import dagger.android.DaggerApplication

open class TestApplication : DaggerApplication() {

    override fun applicationInjector(): AndroidInjector<out DaggerApplication> {
        return DaggerTestAppComponent
            .builder()
            .application(this)
            .build()
    }
}