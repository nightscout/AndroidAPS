package app.aaps.plugins.aps.utils

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StaticInjector @Inject constructor(
    private val injector: HasAndroidInjector
) : HasAndroidInjector {

    companion object {

        private var instance: StaticInjector? = null

        @Deprecated("Use only for classes instantiated by 3rd party")
        fun getInstance(): StaticInjector {
            if (instance == null) throw IllegalStateException("StaticInjector not initialized")
            return instance!!
        }
    }

    init {
        instance = this
    }

    override fun androidInjector(): AndroidInjector<Any> = injector.androidInjector()
}