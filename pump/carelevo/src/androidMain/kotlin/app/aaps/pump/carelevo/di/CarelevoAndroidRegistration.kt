package app.aaps.pump.carelevo.di

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo

/** Android has all three Carelevo ports (see [CarelevoRegistration]), so Carelevo is a pump the user can select here. */
@ContributesTo(AppScope::class)
@BindingContainer(includes = [CarelevoRegistration::class])
object CarelevoAndroidRegistration
