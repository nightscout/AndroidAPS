package info.nightscout.plugins.constraints.storage

import android.os.Environment
import android.os.StatFs
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.annotations.OpenForTesting
import info.nightscout.interfaces.Constants
import info.nightscout.interfaces.constraints.Constraint
import info.nightscout.interfaces.constraints.Constraints
import info.nightscout.interfaces.notifications.Notification
import info.nightscout.interfaces.plugin.PluginBase
import info.nightscout.interfaces.plugin.PluginDescription
import info.nightscout.interfaces.plugin.PluginType
import info.nightscout.interfaces.ui.UiInteraction
import info.nightscout.plugins.constraints.R
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.interfaces.ResourceHelper
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
), Constraints {

    override fun isClosedLoopAllowed(value: Constraint<Boolean>): Constraint<Boolean> {
        val diskFree = availableInternalMemorySize()
        if (diskFree < Constants.MINIMUM_FREE_SPACE) {
            aapsLogger.debug(LTag.CONSTRAINTS, "Closed loop disabled. Internal storage free (Mb):$diskFree")
            value.set(aapsLogger, false, rh.gs(R.string.disk_full, Constants.MINIMUM_FREE_SPACE), this)
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