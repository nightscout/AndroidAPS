package app.aaps.desktop.shell.platform

import app.aaps.plugins.source.RandomBgWakeLock
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * No wake lock on the desktop. A desktop app keeps running while its window is open, and nothing here
 * stops a computer from sleeping, so `RandomBgPlugin` makes readings while the computer is awake.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class DesktopRandomBgWakeLock : RandomBgWakeLock {

    override fun acquire(): RandomBgWakeLock.Held? = null
}
