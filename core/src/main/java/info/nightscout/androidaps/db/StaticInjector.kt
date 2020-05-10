package info.nightscout.androidaps.db

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import java.lang.IllegalStateException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@Deprecated("Only until DB is refactored")
class StaticInjector @Inject constructor(
    private val injector: HasAndroidInjector
) : HasAndroidInjector {
    companion object {
        @Deprecated("Only until DB is refactored")
        private var instance : StaticInjector? = null

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