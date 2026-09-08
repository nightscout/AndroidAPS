package app.aaps.wear.tile

import app.aaps.core.interfaces.di.injectMetroMembers
import app.aaps.wear.di.WearMetroService
import app.aaps.wear.tile.source.TempTargetSource
import dev.zacsweers.metro.Inject

class TempTargetTileService : TileBase() {

    @Inject lateinit var tempTargetSource: TempTargetSource

    // Not derived from WearMetroService, do injection here
    override fun onCreate() {
        injectMetroMembers(this)
        super.onCreate()
    }

    override val resourceVersion = "TempTargetTileService"
    override val source get() = tempTargetSource
}
