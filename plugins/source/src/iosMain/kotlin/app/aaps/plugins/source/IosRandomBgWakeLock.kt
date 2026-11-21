package app.aaps.plugins.source

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * No wake lock: iOS has none.
 *
 * Nothing on iOS keeps an app running once the system suspends it. `isIdleTimerDisabled` only stops the
 * screen locking while the app is in front, and a background task buys about half a minute. So
 * [RandomBgPlugin] makes readings while the app runs, and stops when it is suspended - which is fine for
 * what it is for, testing on the simulator or a phone with the app open.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class IosRandomBgWakeLock : RandomBgWakeLock {

    override fun acquire(): RandomBgWakeLock.Held? = null
}
