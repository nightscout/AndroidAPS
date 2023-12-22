package app.aaps.di

import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.database.persistence.CompatDBHelper
import app.aaps.di.DaggerTestAppComponent
import app.aaps.implementation.plugin.PluginStore
import app.aaps.plugins.aps.utils.StaticInjector
import dagger.android.AndroidInjector
import dagger.android.DaggerApplication
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject

open class TestApplication : DaggerApplication() {

    @Inject lateinit var pluginStore: PluginStore
    @Inject lateinit var configBuilder: ConfigBuilder
    @Inject lateinit var compatDBHelper: CompatDBHelper
    @Inject lateinit var plugins: List<@JvmSuppressWildcards PluginBase>
    @Suppress("unused") @Inject lateinit var staticInjector: StaticInjector// better avoid, here fake only to initialize

    private val disposable = CompositeDisposable()
    override fun onCreate() {
        super.onCreate()
        disposable += compatDBHelper.dbChangeDisposable()
        // Register all tabs in app here
        pluginStore.plugins = plugins
        configBuilder.initialize()

    }

    override fun applicationInjector(): AndroidInjector<out DaggerApplication> {
        return DaggerTestAppComponent
            .builder()
            .application(this)
            .build()
    }
}