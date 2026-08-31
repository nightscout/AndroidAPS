package app.aaps.wear.di

import android.app.Service
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import app.aaps.core.interfaces.di.injectMetroMembers
import app.aaps.wear.di.WearMetroActivity
import app.aaps.wear.di.WearMetroService

/**
 * Base classes for the Android entry points in this module, replacing `WearMetroActivity` and
 * `WearMetroService`.
 * `:core:objects` and `:core:ui` already carry `MetroService` and `MetroAppCompatActivity`, but this
 * module depends on neither, and taking a dependency on them just to inherit two one-line bodies is
 * not worth it. `injectMetroMembers` lives in `:core:interfaces`, which wear already has - exactly the
 * case `MetroAndroidEntryPoints` describes for classes that cannot use its bases.
 */
abstract class WearMetroActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        injectMetroMembers(this)
        super.onCreate(savedInstanceState)
    }
}

/** Metro's answer to `WearMetroService`. Subclasses must call `super.onCreate()` first. */
abstract class WearMetroService : Service() {

    override fun onCreate() {
        injectMetroMembers(this)
        super.onCreate()
    }
}
