package app.aaps.wear.tile

import app.aaps.wear.tile.source.SceneSource
import dagger.android.AndroidInjection
import javax.inject.Inject

class SceneTileService : TileBase() {

    @Inject lateinit var sceneSource: SceneSource

    // Not derived from DaggerService, do injection here
    override fun onCreate() {
        AndroidInjection.inject(this)
        super.onCreate()
    }

    override val resourceVersion = "SceneTileService"
    override val source get() = sceneSource
}
