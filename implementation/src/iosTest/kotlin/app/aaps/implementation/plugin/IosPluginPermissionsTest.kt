package app.aaps.implementation.plugin

import app.aaps.core.interfaces.aps.APS
import app.aaps.core.interfaces.source.BgSource
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.constraints.Objectives
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PermissionGroup
import app.aaps.core.interfaces.plugin.PermissionProvider
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.pump.Pump
import app.aaps.core.interfaces.pump.PumpWithConcentration
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.aps.Sensitivity
import app.aaps.core.interfaces.smoothing.Smoothing
import app.aaps.core.interfaces.calibration.Calibration
import app.aaps.core.interfaces.sync.Sync
import app.aaps.core.interfaces.constraints.Safety
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * That iOS reports no permissions, and says so loudly if that ever stops being right.
 *
 * Empty is easy to mistake for unfinished, so the second half matters as much as the first: the
 * class asks the plugins before answering, and an answer it cannot act on has to reach the log
 * rather than vanish. A permission listed as missing on iOS would send the user looking for a
 * setting that does not exist; one listed as granted would claim a check nothing performed.
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
        override fun shortTextMode(): Boolean = false
    }

    private class FakePlugin(
        logger: AAPSLogger,
        private val enabled: Boolean,
        private val groups: List<PermissionGroup>
    ) : PluginBase(PluginDescription().also { it.mainType = PluginType.GENERAL }, logger, SilentText) {

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
        override fun verifySelectionInCategories() = unused()
        override fun getSpecificPluginsList(type: PluginType): ArrayList<PluginBase> = unused()
        override fun beforeImport() = unused()
        override fun afterImport() = unused()

        private fun unused(): Nothing = throw AssertionError("IosPluginPermissions must only read the plugin list")
    }

    private val logger = RecordingLogger()

    private fun group(vararg permissions: String) = PermissionGroup(
        permissions = permissions.toList(),
        rationaleTitle = TextRef.Literal("why"),
        rationaleDescription = TextRef.Literal("because")
    )

    private fun sut(
        plugins: List<PluginBase> = emptyList(),
        providers: Set<PermissionProvider> = emptySet()
    ) = IosPluginPermissions(logger, FakeActivePlugin(plugins)) { providers }

    @Test
    fun `with no plugins at all both lists are empty`() {
        assertTrue(sut().collectMissingPermissions().isEmpty())
        assertTrue(sut().collectAllPermissions().isEmpty())
        assertTrue(logger.errors.isEmpty())
    }

    @Test
    fun `a plugin that wants nothing produces no complaint`() {
        val sut = sut(plugins = listOf(FakePlugin(logger, enabled = true, groups = emptyList())))

        assertTrue(sut.collectAllPermissions().isEmpty())
        assertTrue(logger.errors.isEmpty())
    }

    /** The guard. Today nothing reaches this, and the day something does it must not pass quietly. */
    @Test
    fun `a plugin that declares a permission is reported as a problem`() {
        val sut = sut(plugins = listOf(FakePlugin(logger, enabled = true, groups = listOf(group("some.permission")))))

        assertTrue(sut.collectAllPermissions().isEmpty(), "still nothing the screen can act on")
        assertEquals(1, logger.errors.size)
        assertTrue(logger.errors.single().contains("some.permission"))
    }

    @Test
    fun `a disabled plugin is not asked`() {
        val sut = sut(plugins = listOf(FakePlugin(logger, enabled = false, groups = listOf(group("some.permission")))))

        assertTrue(sut.collectAllPermissions().isEmpty())
        assertTrue(logger.errors.isEmpty())
    }

    @Test
    fun `a permission provider is asked as well as the plugins`() {
        val provider = object : PermissionProvider {
            override fun requiredPermissions(): List<PermissionGroup> = listOf(group("provider.permission"))
        }
        val sut = sut(providers = setOf(provider))

        assertTrue(sut.collectMissingPermissions().isEmpty())
        assertTrue(logger.errors.single().contains("provider.permission"))
    }

    /** Both entry points behave the same, so neither can drift into inventing a group. */
    @Test
    fun `missing and all agree`() {
        val sut = sut(plugins = listOf(FakePlugin(logger, enabled = true, groups = listOf(group("a", "b")))))

        assertEquals(sut.collectAllPermissions(), sut.collectMissingPermissions())
    }
}
