package app.aaps.core.objects.di

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.objects.crypto.CryptoUtil
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * The Android-only half of this module's bindings.
 * Same shape as `ApsPluginRegistrations` in `:plugins:aps` androidMain. Metro aggregates contributions
 * off the compile classpath, so this merges into the root graph when `:app` is compiled.
 */
@ContributesTo(AppScope::class)
@BindingContainer
object CoreObjectsAndroidContainer {

    @Provides
    @SingleIn(AppScope::class)
    fun cryptoUtil(aapsLogger: AAPSLogger): CryptoUtil = CryptoUtil(aapsLogger)
}
