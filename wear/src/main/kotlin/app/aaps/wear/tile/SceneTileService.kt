package app.aaps.wear.tile

import app.aaps.core.interfaces.di.injectMetroMembers
import app.aaps.wear.di.WearMetroService
import app.aaps.wear.tile.source.SceneSource
import dev.zacsweers.metro.Inject

class SceneTileService : TileBase() {

    @Inject lateinit var sceneSource: SceneSource

    // Not derived from WearMetroService, do injection here
    override fun onCreate() {
        injectMetroMembers(this)
        super.onCreate()
    }

    override val resourceVersion = "SceneTileService"
    override val source get() = sceneSource
}
