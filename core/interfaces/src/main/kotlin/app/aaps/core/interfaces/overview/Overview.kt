package app.aaps.core.interfaces.overview

import android.widget.TextView
import app.aaps.core.interfaces.configuration.ConfigExportImport
import app.aaps.core.interfaces.rx.bus.RxBus

interface Overview : ConfigExportImport {

    val overviewBus: RxBus

    /**
     * Set textView content with version and warning color
     */
    fun setVersionView(view: TextView)
}