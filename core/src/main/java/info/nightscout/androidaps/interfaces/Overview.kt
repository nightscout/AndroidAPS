package info.nightscout.androidaps.interfaces

import androidx.annotation.StringRes
import info.nightscout.androidaps.plugins.bus.RxBus

interface Overview : ConfigExportImport {

    val overviewBus: RxBus

    fun addNotification(id: Int, text: String, level: Int, @StringRes actionButtonId: Int, action: Runnable)
    fun dismissNotification(id: Int)
}