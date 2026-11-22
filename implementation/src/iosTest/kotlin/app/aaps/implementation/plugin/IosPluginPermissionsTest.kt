package app.aaps.implementation.plugin

import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.aps.APS
import app.aaps.core.interfaces.aps.Sensitivity
import app.aaps.core.interfaces.calibration.Calibration
import app.aaps.core.interfaces.constraints.Objectives
import app.aaps.core.interfaces.constraints.Safety
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.AapsNotification
import app.aaps.core.interfaces.notifications.AlarmSound
import app.aaps.core.interfaces.notifications.NotificationAction
import app.aaps.core.interfaces.notifications.NotificationHandle
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationLevel
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.IosPermission
import app.aaps.core.interfaces.plugin.PermissionGroup
import app.aaps.core.interfaces.plugin.PermissionProvider
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.pump.Pump
import app.aaps.core.interfaces.pump.PumpWithConcentration
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.smoothing.Smoothing
import app.aaps.core.interfaces.source.BgSource
import app.aaps.core.interfaces.sync.Sync
import app.aaps.core.keys.interfaces.TextRef
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What the iOS permission screen lists, and when a group counts as missing.
 *
 * A refused permission on iOS is silent - alarms that never show, a pump that cannot be reached - so
 * what matters is that a refusal is listed, that a guess is not, and that a group iOS cannot check is
 * neither shown nor dropped without a word.
 */
class IosPluginPermissionsTest {

    private class RecordingLogger : AAPSLogger {

        val errors = mutableListOf<String>()

        override fun error(tag: LTag, message: String) {
            errors.add(message)
        }

        override fun debug(message: String) {}
        override fun debug(enable: Boolean, tag: LTag, message: String) {}
        override fun debug(tag: LTag, message: String) {}
        override fun debug(tag: LTag, accessor: () -> String) {}
        override fun debug(tag: LTag, format: String, vararg arguments: Any?) {}
        override fun warn(tag: LTag, message: String) {}
        override fun warn(tag: LTag, format: String, vararg arguments: Any?) {}
        override fun info(tag: LTag, message: String) {}
        override fun info(tag: LTag, format: String, vararg arguments: Any?) {}
        override fun error(tag: LTag, message: String, throwable: Throwable) {}
        override fun error(tag: LTag, format: String, vararg arguments: Any?) {}
        override fun error(message: String) {}
        override fun error(message: String, throwable: Throwable) {}
        override fun error(format: String, vararg arguments: Any?) {}
        override fun debug(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) {}
        override fun info(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) {}
        override fun warn(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) {}
        override fun error(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) {}
    }

    private object SilentText : TextResolver {

        override fun gs(ref: TextRef): String = ""
        override fun gs(ref: TextRef, vararg args: Any?): String = ""
        override fun gsNotLocalised(ref: TextRef): String = ""
    }

    /** PluginBase needs one; nothing here posts a notification, so every call is a no-op. */
    private object NoNotifications : NotificationManager {

        override val notifications: StateFlow<List<AapsNotification>> = MutableStateFlow(emptyList())
        override fun cleanUp() {}
        override fun post(id: NotificationId, text: String, level: NotificationLevel, validMinutes: Int, sound: AlarmSound?, actions: List<NotificationAction>, validityCheck: (() -> Boolean)?) = NotificationHandle(0)
        override fun post(
            id: NotificationId, text: String, level: NotificationLevel, date: Long, validTo: Long, sound: AlarmSound?,
            actions: List<NotificationAction>, validityCheck: (() -> Boolean)?, alarmTitle: String?, canMute: Boolean,
            tellWatch: Boolean
        ) = NotificationHandle(0)
        override fun post(id: NotificationId, textRef: TextRef, level: NotificationLevel, validMinutes: Int, date: Long, validTo: Long, sound: AlarmSound?, actions: List<NotificationAction>, validityCheck: (() -> Boolean)?) = NotificationHandle(0)
        override fun dismiss(id: NotificationId) {}
        override fun dismiss(handle: NotificationHandle) {}
        override fun muteAllAlarms() {}
    }

    private class FakePlugin(
        logger: AAPSLogger,
        private val enabled: Boolean,
        private val groups: List<PermissionGroup>
    ) : PluginBase(PluginDescription().also { it.mainType = PluginType.GENERAL }, logger, SilentText, NoNotifications) {

        override fun isEnabled(): Boolean = enabled
        override fun requiredPermissions(): List<PermissionGroup> = groups
    }

    /** Only `getPluginsList` is ever called. The rest would be a bug if it were. */
    private class FakeActivePlugin(private val plugins: List<PluginBase>) : ActivePlugin {

        override fun getPluginsList(): ArrayList<PluginBase> = ArrayList(plugins)

        override val activeBgSource: BgSource get() = unused()
        override val activeAPS: APS? get() = unused()
        override val activePump: PumpWithConcentration get() = unused()
        override val activePumpInternal: Pump get() = unused()
        override val activeSensitivity: Sensitivity get() = unused()
        override val activeSafety: Safety get() = unused()
        override val activeIobCobCalculator: IobCobCalculator get() = unused()
        override val activeObjectives: Objectives? get() = unused()
        override val activeSmoothing: Smoothing get() = unused()
        override val activeCalibration: Calibration get() = unused()
        override val firstActiveSync: Sync? get() = unused()
        override val activeSyncs: ArrayList<Sync> get() = unused()
        override fun getSpecificPluginsVisibleInList(type: PluginType): ArrayList<PluginBase> = unused()
        override fun getSpecificPluginsListByInterface(interfaceClass: KClass<*>): ArrayList<PluginBase> = unused()
        override fun verifySelectionInCategories(): List<Job> = unused()
        override fun getSpecificPluginsList(type: PluginType): ArrayList<PluginBase> = unused()
        override fun beforeImport() = unused()
        override fun afterImport() = unused()

        private fun unused(): Nothing = throw AssertionError("IosPluginPermissions must only read the plugin list")
    }

    private class FakeStatus(val states: MutableMap<String, IosPermissionState> = mutableMapOf()) : IosPermissionStatus {

        override fun state(permission: String): IosPermissionState = states[permission] ?: IosPermissionState.GRANTED
        override fun refresh(onDone: () -> Unit) = onDone()
        override fun ask(permission: String, onDone: () -> Unit) = onDone()
    }

    private val logger = RecordingLogger()
    private val status = FakeStatus()

    private fun group(vararg permissions: String) = PermissionGroup(
        permissions = permissions.toList(),
        rationaleTitle = TextRef.Literal("why"),
        rationaleDescription = TextRef.Literal("because")
    )

    private fun sut(
        plugins: List<PluginBase> = emptyList(),
        providers: Set<PermissionProvider> = emptySet()
    ) = IosPluginPermissions(logger, FakeActivePlugin(plugins), status) { providers }

    private fun List<PermissionGroup>.ids() = map { it.permissions }

    @Test
    fun `notifications are always listed also with no plugins`() {
        assertEquals(listOf(listOf(IosPermission.NOTIFICATIONS)), sut().collectAllPermissions().ids())
        assertTrue(logger.errors.isEmpty())
    }

    @Test
    fun `granted and not yet read are not missing`() {
        status.states[IosPermission.NOTIFICATIONS] = IosPermissionState.GRANTED
        assertTrue(sut().collectMissingPermissions().isEmpty())

        status.states[IosPermission.NOTIFICATIONS] = IosPermissionState.UNKNOWN
        assertTrue(sut().collectMissingPermissions().isEmpty(), "a guess must not open the sheet")
    }

    /** A refusal is the case this class exists for: without it, alarms are silent and nothing says so. */
    @Test
    fun `refused and never asked are missing`() {
        status.states[IosPermission.NOTIFICATIONS] = IosPermissionState.DENIED
        assertEquals(listOf(listOf(IosPermission.NOTIFICATIONS)), sut().collectMissingPermissions().ids())

        status.states[IosPermission.NOTIFICATIONS] = IosPermissionState.NOT_ASKED
        assertEquals(listOf(listOf(IosPermission.NOTIFICATIONS)), sut().collectMissingPermissions().ids())
    }

    @Test
    fun `an enabled pump adds Bluetooth and a refusal shows`() {
        status.states[IosPermission.BLUETOOTH] = IosPermissionState.DENIED
        val sut = sut(plugins = listOf(FakePlugin(logger, enabled = true, groups = listOf(group(IosPermission.BLUETOOTH)))))

        assertEquals(listOf(listOf(IosPermission.NOTIFICATIONS), listOf(IosPermission.BLUETOOTH)), sut.collectAllPermissions().ids())
        assertEquals(listOf(listOf(IosPermission.BLUETOOTH)), sut.collectMissingPermissions().ids())
    }

    @Test
    fun `a disabled plugin is not asked`() {
        val sut = sut(plugins = listOf(FakePlugin(logger, enabled = false, groups = listOf(group(IosPermission.BLUETOOTH)))))

        assertEquals(listOf(listOf(IosPermission.NOTIFICATIONS)), sut.collectAllPermissions().ids())
    }

    @Test
    fun `the same group from two plugins is listed once`() {
        val pump = FakePlugin(logger, enabled = true, groups = listOf(group(IosPermission.BLUETOOTH)))
        val other = FakePlugin(logger, enabled = true, groups = listOf(group(IosPermission.BLUETOOTH)))

        assertEquals(1, sut(plugins = listOf(pump, other)).collectAllPermissions().count { it.permissions == listOf(IosPermission.BLUETOOTH) })
    }

    @Test
    fun `a permission provider is asked as well as the plugins`() {
        status.states[IosPermission.LOCATION] = IosPermissionState.NOT_ASKED
        val provider = object : PermissionProvider {
            override fun requiredPermissions(): List<PermissionGroup> = listOf(group(IosPermission.LOCATION))
        }

        assertEquals(listOf(listOf(IosPermission.LOCATION)), sut(providers = setOf(provider)).collectMissingPermissions().ids())
    }

    /** The guard: a group iOS cannot check is neither shown nor dropped quietly. */
    @Test
    fun `a permission iOS cannot check is left out and logged`() {
        val sut = sut(plugins = listOf(FakePlugin(logger, enabled = true, groups = listOf(group("android.permission.SOMETHING")))))

        assertEquals(listOf(listOf(IosPermission.NOTIFICATIONS)), sut.collectAllPermissions().ids())
        assertEquals(1, logger.errors.size)
        assertTrue(logger.errors.single().contains("android.permission.SOMETHING"))
    }

    @Test
    fun `a group that mixes a known and an unknown id is left out as well`() {
        val sut = sut(plugins = listOf(FakePlugin(logger, enabled = true, groups = listOf(group(IosPermission.BLUETOOTH, "other")))))

        assertEquals(listOf(listOf(IosPermission.NOTIFICATIONS)), sut.collectAllPermissions().ids())
        assertTrue(logger.errors.single().contains("other"))
    }
}
