package app.aaps.pump.carelevo.di

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo

/**
 * iOS has what CareLevo needs (see [CarelevoRegistration]), so CareLevo is a pump the user can
 * select here:
 *
 * - `IosCarelevoAppForeground` on the UIKit application state,
 * - `IosCarelevoBleReadyCheck` on the CoreBluetooth adapter state,
 * - `PumpAlerts`, bound by `PumpAlertsIosBindings` on `IosPumpAlertPlatform`.
 *
 * There used to be a third CareLevo port here, `IosCarelevoSystemNotifier`, which put a patch alarm
 * in the phone's notification list. Pump alerts are one path in the core now, the same for every
 * driver and every platform, so the driver does not carry a notifier of its own any more.
 *
 * The Android counterpart is `CarelevoAndroidRegistration`. Both exist so that a platform without
 * the ports has no CareLevo at all, rather than a pump that cannot work.
 */
@ContributesTo(AppScope::class)
@BindingContainer(includes = [CarelevoRegistration::class])
object CarelevoIosRegistration
