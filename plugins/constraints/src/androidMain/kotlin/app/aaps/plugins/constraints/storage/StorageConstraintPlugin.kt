package app.aaps.plugins.constraints.storage

import app.aaps.core.keys.interfaces.TextRef.Companion.withArgs
import app.aaps.plugins.constraints.ConstraintsStrings
import android.os.Environment
import android.os.StatFs
import app.aaps.annotations.OpenForTesting
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.constraints.Constraint
import app.aaps.core.interfaces.constraints.PluginConstraints
import app.aaps.core.interfaces.di.APS
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.keys.interfaces.TextRef
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.IntKey
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

@OpenForTesting
// Registers itself into the plugin list. Scoped with Metro's @SingleIn, not javax @Singleton: a
// contributed class is built by the graph generated in `:app`, which has no Dagger interop, so a javax
// scope there is ignored and every read would build a new plugin.
@ContributesIntoMap(AppScope::class, binding = binding<PluginBase>())
@APS
@IntKey(820)
@SingleIn(AppScope::class)
class StorageConstraintPlugin @Inject constructor(
    aapsLogger: AAPSLogger,
    override val rh: ResourceHelper,
    private val notificationManager: NotificationManager
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.CONSTRAINTS)
        .alwaysEnabled(true)
        .showInList { false }
        .pluginName(ConstraintsStrings.storage),
    aapsLogger, rh
), PluginConstraints {

    override suspend fun isClosedLoopAllowed(value: Constraint<Boolean>): Constraint<Boolean> {
        val diskFree = availableInternalMemorySize()
        if (diskFree < Constants.MINIMUM_FREE_SPACE) {
            aapsLogger.debug(LTag.CONSTRAINTS, "Closed loop disabled. Internal storage free (Mb):$diskFree")
            value.set(false, rh.gs(ConstraintsStrings.disk_full, Constants.MINIMUM_FREE_SPACE), this)
            notificationManager.post(NotificationId.DISK_FULL, ConstraintsStrings.disk_full.withArgs(Constants.MINIMUM_FREE_SPACE))
        }
        return value
    }

    fun availableInternalMemorySize(): Long {
        val path = Environment.getDataDirectory()
        val stat = StatFs(path.path)
        val blockSize = stat.blockSizeLong
        val blocksAvailable = stat.availableBlocksLong
        val size = 1048576 // block size of 1 Mb
        return blocksAvailable * blockSize / size
    }
}