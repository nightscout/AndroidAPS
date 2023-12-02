package app.aaps.runners

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import app.aaps.di.TestApplication

class InjectedTestRunner : AndroidJUnitRunner() {

    override fun newApplication(cl: ClassLoader?, name: String?, context: Context?): Application {
        return super.newApplication(cl, TestApplication::class.java.name, context)
    }
}