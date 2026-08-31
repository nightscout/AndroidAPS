package app.aaps.core.interfaces.di

import android.content.Context

/**
 * Fills the injected fields of an Android built object from the application's Metro graph.
 * Android constructs activities, services and receivers itself, so they cannot take dependencies in a
 * constructor - they have to reach out and ask. `MetroAppCompatActivity`, `MetroService` and
 * `MetroBroadcastReceiver` in `:core:objects` are one line wrappers around this call.
 * It lives here, beside [MetroMemberInjector], rather than next to those base classes, because a class
 * that already extends a framework base of its own cannot use them and has to call this directly.
 * Several do: `WearableListenerService` and `NotificationListenerService` subclasses, the
 * `AppWidgetProvider` widgets, and the pump services in modules that do not depend on `:core:objects`.
 * Putting it in `:core:interfaces` means none of them needs a new module dependency to be injected.
 */
fun Context.injectMetroMembers(target: Any) {
    val application = applicationContext
    check(application is MetroMemberInjector) {
        "Application does not implement MetroMemberInjector, so ${target::class.java.name} cannot be injected"
    }
    check(application.injectMembers(target)) {
        "No Metro binding for ${target::class.java.name}. Add a @Provides @IntoMap @ClassKey entry for it."
    }
}
