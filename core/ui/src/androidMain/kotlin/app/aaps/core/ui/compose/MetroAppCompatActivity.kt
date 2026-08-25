package app.aaps.core.ui.compose

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import app.aaps.core.interfaces.di.MetroMemberInjector

/**
 * Metro's answer to `DaggerAppCompatActivity` and to Hilt's `@AndroidEntryPoint` on an activity.
 * Subclasses must call `super.onCreate(...)` first.
 *
 * It lives here rather than beside `MetroBroadcastReceiver` and `MetroService` in `:core:objects`,
 * because AppCompat is a `:core:ui` dependency and adding it to `:core:objects` would buy nothing.
 *
 * 34 activities use `DaggerAppCompatActivity` today. For those, converting is a one word change.
 * An activity that also needs a view model gets it from [MetroViewModelFactoryOwner], the same way.
 *
 * A missing binding fails loudly here too - see `MetroAndroidEntryPoints` for why.
 */
abstract class MetroAppCompatActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val application = applicationContext
        check(application is MetroMemberInjector) {
            "Application does not implement MetroMemberInjector, so ${this::class.java.name} cannot be injected"
        }
        check(application.injectMembers(this)) {
            "No Metro binding for ${this::class.java.name}. Add a @Provides @IntoMap @ClassKey entry for it."
        }
        super.onCreate(savedInstanceState)
    }
}
