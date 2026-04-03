package app.aaps.wear.tile

import app.aaps.wear.tile.source.RunningModeSource
import dagger.android.AndroidInjection
import javax.inject.Inject

class RunningModeTileService : TileBase() {

    @Inject lateinit var runningModeSource: RunningModeSource

    // Not derived from DaggerService, do injection here
    override fun onCreate() {
        AndroidInjection.inject(this)
        super.onCreate()
    }

    override val resourceVersion = "RunningModeTileService"
    override val source get() = runningModeSource
}


