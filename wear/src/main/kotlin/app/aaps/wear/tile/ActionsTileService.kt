package app.aaps.wear.tile

import app.aaps.core.interfaces.di.injectMetroMembers
import app.aaps.wear.di.WearMetroService
import app.aaps.wear.tile.source.ActionSource
import dev.zacsweers.metro.Inject

class ActionsTileService : TileBase() {

    @Inject lateinit var actionSource: ActionSource

    // Not derived from WearMetroService, do injection here
    override fun onCreate() {
        injectMetroMembers(this)
        super.onCreate()
    }

    override val resourceVersion = "ActionsTileService"
    override val source get() = actionSource
}
