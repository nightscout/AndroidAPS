package app.aaps.implementation.di

import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.PermissionProvider
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.implementation.notifications.NotificationManagerImpl
import app.aaps.implementation.profile.ProfileFunctionImpl
import app.aaps.implementation.resources.ResourceHelperImpl
import app.aaps.implementation.utils.fabric.FabricPrivacyImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds

/**
 * What is left of this module after the Metro migration. Everything else in `:implementation` is
 * either a Metro contribution on the class or a `@Provides` in [ImplementationBindings].
 *
 * The four classes below stay on Dagger because building them touches Android at construction time
 * (`LocaleHelper.currentLocale`, `createNotificationChannel`, `persistenceLayer.observeChanges`,
 * `Firebase.analytics`), and a Metro contribution is built when the graph is - which would break the
 * plain-JVM graph tests. They are handed to Metro through `AapsLeaves` instead.
 */
@Module
@InstallIn(SingletonComponent::class)
@Suppress("unused")
interface ImplementationModule {

    // Runtime-permission sources for non-plugin features (e.g. standalone Automation).
    // May be empty; contributors bind via @IntoSet PermissionProvider. Metro borrows the finished
    // set through the `permissionProviders` leaf, so this declaration stays here.
    @Multibinds fun permissionProviders(): Set<PermissionProvider>

    @Binds fun bindFabricPrivacy(fabricPrivacyImpl: FabricPrivacyImpl): FabricPrivacy
    @Binds fun bindResourceHelper(resourceHelperImpl: ResourceHelperImpl): ResourceHelper
    @Binds fun bindNotificationManager(notificationManagerImpl: NotificationManagerImpl): NotificationManager
    @Binds fun bindsProfileFunction(profileFunctionImpl: ProfileFunctionImpl): ProfileFunction
}
