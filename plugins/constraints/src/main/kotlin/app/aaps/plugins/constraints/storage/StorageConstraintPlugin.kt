package app.aaps.plugins.constraints.storage

import android.os.Environment
import android.os.StatFs
import app.aaps.annotations.OpenForTesting
import app.aaps.core.interfaces.configuration.Constants
import app.aaps.core.interfaces.constraints.Constraint
import app.aaps.core.interfaces.constraints.PluginConstraints
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.plugin.PluginType
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.plugins.constraints.R
import dagger.android.HasAndroidInjector
import javax.inject.Inject
import javax.inject.Singleton

@OpenForTesting
@Singleton
class StorageConstraintPlugin @Inject constructor(
    injector: HasAndroidInjector,
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    private val activeNames: UiInteraction
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.CONSTRAINTS)
        .neverVisible(true)
        .alwaysEnabled(true)
        .showInList(false)
        .pluginName(R.string.storage),
    aapsLogger, rh, injector
), PluginConstraints {

    override fun isClosedLoopAllowed(value: Constraint<Boolean>): Constraint<Boolean> {
        val diskFree = availableInternalMemorySize()
        if (diskFree < Constants.MINIMUM_FREE_SPACE) {
            aapsLogger.debug(LTag.CONSTRAINTS, "Closed loop disabled. Internal storage free (Mb):$diskFree")
            value.set(false, rh.gs(R.string.disk_full, Constants.MINIMUM_FREE_SPACE), this)
            activeNames.addNotification(Notification.DISK_FULL, rh.gs(R.string.disk_full, Constants.MINIMUM_FREE_SPACE), Notification.NORMAL)
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