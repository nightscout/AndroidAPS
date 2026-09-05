package app.aaps.core.objects.di

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.protection.PasswordHasher
import app.aaps.core.objects.crypto.CryptoUtil
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * The JVM half of this module's bindings - Android and desktop both.
 * Same shape as `ApsPluginRegistrations` in `:plugins:aps` androidMain. Metro aggregates contributions
 * off the compile classpath, so this merges into the root graph when `:app` is compiled.
 */
@ContributesTo(AppScope::class)
@BindingContainer
object CoreObjectsJvmContainer {

    @Provides
    @SingleIn(AppScope::class)
    fun cryptoUtil(aapsLogger: AAPSLogger): CryptoUtil = CryptoUtil(aapsLogger)

    // The same instance under its shared-code interface, so a commonMain caller can compare a
    // password without naming CryptoUtil, which is Android only.
    @Provides
    fun passwordHasher(cryptoUtil: CryptoUtil): PasswordHasher = cryptoUtil
}
