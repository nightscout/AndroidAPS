package app.aaps.core.objects.workflow

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.aaps.core.interfaces.di.injectMetroMembers

/**
 * Base classes for Android entry points that Metro injects, mirroring `dagger.android`.
 *
 * Android builds these classes itself, so they cannot take dependencies in a constructor - they have
 * to reach out and ask. Metro ships no base classes for that, being deliberately not an
 * inheritance-based framework. Without these, every converted class repeats the same line, and
 * repeats the chance of getting it wrong: calling it too late, after a field has already been read.
 *
 * The contract is copied from `dagger.android` on purpose - inject in the same place, with the same
 * `super` call - so converting a class is a one word change and its body does not move:
 *
 *     class MyReceiver : DaggerBroadcastReceiver()   ->   class MyReceiver : MetroBroadcastReceiver()
 *
 * There are 84 such classes in this tree: 34 activities, 31 services, 19 receivers. The activity base
 * is `MetroAppCompatActivity` in `:core:ui`, which is where the AppCompat dependency lives.
 *
 * Some classes cannot use these, because they already extend a framework base of their own - a
 * `WearableListenerService`, an `AppWidgetProvider`, or a pump service in a module that does not depend
 * on `:core:objects`. Those call [injectMetroMembers] directly, which is all these base classes do
 * anyway. That function lives in `:core:interfaces` beside the injector interface, so calling it needs
 * no dependency on this module.
 *
 * Metro's answer to `DaggerBroadcastReceiver`. Subclasses must call `super.onReceive(...)` first.
 */
abstract class MetroBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        context.injectMetroMembers(this)
    }
}

/** Metro's answer to `DaggerService`. Subclasses must call `super.onCreate()` first. */
abstract class MetroService : Service() {

    override fun onCreate() {
        injectMetroMembers(this)
        super.onCreate()
    }
}
