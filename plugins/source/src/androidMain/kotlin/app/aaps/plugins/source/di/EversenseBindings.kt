package app.aaps.plugins.source.di

import android.bluetooth.BluetoothManager
import android.content.Context
import app.aaps.plugins.eversense.EversenseCGMPlugin
import app.aaps.plugins.source.eversense.EversenseStateStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

@ContributesTo(AppScope::class)
@BindingContainer
object EversenseBindings {
    @Provides
    @SingleIn(AppScope::class)
    fun provideEversense(context: Context, store: EversenseStateStore): EversenseCGMPlugin {
        val appContext = context.applicationContext
        val manager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        return EversenseCGMPlugin(appContext, manager, store)
    }
}
