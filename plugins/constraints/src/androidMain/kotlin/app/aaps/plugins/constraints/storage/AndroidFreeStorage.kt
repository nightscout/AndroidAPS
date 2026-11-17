package app.aaps.plugins.constraints.storage

import android.os.Environment
import android.os.StatFs
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

/** The free space of the data partition, where the app's database is. */
@ContributesBinding(AppScope::class)
@Inject
class AndroidFreeStorage : FreeStorage {

    override fun availableMb(): Long {
        val stat = StatFs(Environment.getDataDirectory().path)
        return stat.availableBlocksLong * stat.blockSizeLong / BYTES_IN_MB
    }

    private companion object {

        const val BYTES_IN_MB = 1048576
    }
}
