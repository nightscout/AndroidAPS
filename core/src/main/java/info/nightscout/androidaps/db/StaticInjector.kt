package info.nightscout.androidaps.db

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import java.lang.IllegalStateException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StaticInjector @Inject constructor(
    private val injector: HasAndroidInjector
) : HasAndroidInjector {
    companion object {
        private var instance : StaticInjector? = null

        @Deprecated("Only until DB is refactored")
        fun getInstance() : StaticInjector {
            if (instance == null) throw IllegalStateException("StaticInjector not initialized")
            return instance!!
        }
    }

    init {
        instance = this
    }
    override fun androidInjector(): AndroidInjector<Any> = injector.androidInjector()
}