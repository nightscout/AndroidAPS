package app.aaps.implementation.plugin

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.IosPermission
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreBluetooth.CBCentralManager
import platform.CoreBluetooth.CBManager
import platform.CoreBluetooth.CBManagerAuthorizationAllowedAlways
import platform.CoreBluetooth.CBManagerAuthorizationNotDetermined
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusEphemeral
import platform.UserNotifications.UNAuthorizationStatusNotDetermined
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNUserNotificationCenter
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.concurrent.Volatile

/** What iOS says about one permission. */
enum class IosPermissionState {

    GRANTED,

    /** Refused, or blocked by a parent or a device policy. Only the Settings app can change it now. */
    DENIED,

    /** Never asked. iOS shows its own question the first time the app asks. */
    NOT_ASKED,

    /** Not read yet. Shown as granted, so the permission sheet does not open on a guess. */
    UNKNOWN
}

/**
 * The state of the permissions in `IosPermission`, as iOS reports them. Behind an interface, so
 * [IosPluginPermissions] can be tested without the system.
 *
 * Bluetooth and location can be read at any time. Notifications can only be read with a callback, so
 * that state is the one from the last [refresh].
 */
interface IosPermissionStatus {

    fun state(permission: String): IosPermissionState

    /** Reads again what needs a callback. [onDone] runs on the main thread. */
    fun refresh(onDone: () -> Unit)

    /**
     * Asks iOS for [permission] when it was never asked. Otherwise opens this app's page in the Settings
     * app: iOS asks only once, so after a refusal that page is the only place left to change it.
     *
     * [onDone] runs on the main thread. For Bluetooth and location iOS gives the answer later; the app
     * becomes active again when its question closes, and the shell reads the states again then.
     */
    fun ask(permission: String, onDone: () -> Unit)
}

@OptIn(ExperimentalForeignApi::class)
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class SystemIosPermissionStatus(
    private val aapsLogger: AAPSLogger
) : IosPermissionStatus {

    @Volatile private var notifications: IosPermissionState = IosPermissionState.UNKNOWN

    /** Kept in a field: iOS shows its question only while the manager that asked still exists. */
    private var bluetoothAsker: CBCentralManager? = null
    private var locationManager: CLLocationManager? = null

    override fun state(permission: String): IosPermissionState = when (permission) {
        IosPermission.BLUETOOTH     -> bluetoothState()
        IosPermission.NOTIFICATIONS -> notifications
        IosPermission.LOCATION      -> locationState()
        else                        -> IosPermissionState.UNKNOWN
    }

    override fun refresh(onDone: () -> Unit) {
        UNUserNotificationCenter.currentNotificationCenter().getNotificationSettingsWithCompletionHandler { settings ->
            notifications = when (settings?.authorizationStatus) {
                UNAuthorizationStatusAuthorized,
                UNAuthorizationStatusProvisional,
                UNAuthorizationStatusEphemeral     -> IosPermissionState.GRANTED

                UNAuthorizationStatusNotDetermined -> IosPermissionState.NOT_ASKED
                null                               -> IosPermissionState.UNKNOWN
                else                               -> IosPermissionState.DENIED
            }
            dispatch_async(dispatch_get_main_queue()) { onDone() }
        }
    }

    override fun ask(permission: String, onDone: () -> Unit) {
        dispatch_async(dispatch_get_main_queue()) {
            if (state(permission) != IosPermissionState.NOT_ASKED) {
                openSettings()
                onDone()
                return@dispatch_async
            }
            when (permission) {
                IosPermission.NOTIFICATIONS -> askNotifications(onDone)

                IosPermission.BLUETOOTH     -> {
                    // Creating a manager is what makes iOS ask. The drivers create their own later.
                    if (bluetoothAsker == null) bluetoothAsker = CBCentralManager(delegate = null, queue = null)
                    onDone()
                }

                IosPermission.LOCATION      -> {
                    location().requestWhenInUseAuthorization()
                    onDone()
                }

                else                        -> onDone()
            }
        }
    }

    /** The same options `IosSystemNotificationPlatform` asks for, so it does not matter which asks first. */
    private fun askNotifications(onDone: () -> Unit) {
        UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge
        ) { granted, error ->
            if (error != null) aapsLogger.error(LTag.NOTIFICATION, "Notification permission failed: $error")
            else aapsLogger.debug(LTag.NOTIFICATION, "Notification permission granted=$granted")
            refresh(onDone)
        }
    }

    private fun bluetoothState(): IosPermissionState = when (CBManager.authorization) {
        CBManagerAuthorizationAllowedAlways -> IosPermissionState.GRANTED
        CBManagerAuthorizationNotDetermined -> IosPermissionState.NOT_ASKED
        else                                -> IosPermissionState.DENIED
    }

    private fun locationState(): IosPermissionState = when (location().authorizationStatus) {
        kCLAuthorizationStatusAuthorizedAlways,
        kCLAuthorizationStatusAuthorizedWhenInUse -> IosPermissionState.GRANTED

        kCLAuthorizationStatusNotDetermined       -> IosPermissionState.NOT_ASKED
        else                                      -> IosPermissionState.DENIED
    }

    private fun location(): CLLocationManager = locationManager ?: CLLocationManager().also { locationManager = it }

    private fun openSettings() {
        val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
        UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any?>(), completionHandler = null)
    }
}
