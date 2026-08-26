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
 *
 * [CoreObjectsGraph] is a **commonMain** container, so it cannot name a class from `androidMain` -
 * that is why `CryptoUtil` stayed on Dagger until now. The fix is not another graph: there is one app
 * graph, in `:app`, and it is Android-only, so it can consume androidMain types happily. What has to
 * match the source set is the *container*, and this is it.
 *
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
