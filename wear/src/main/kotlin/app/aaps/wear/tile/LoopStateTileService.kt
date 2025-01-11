package app.aaps.wear.tile

import app.aaps.wear.tile.source.LoopStateSource
import dagger.android.AndroidInjection
import javax.inject.Inject

class LoopStateTileService : TileBase() {

    @Inject lateinit var loopStateSource: LoopStateSource

    // Not derived from DaggerService, do injection here
    override fun onCreate() {
        AndroidInjection.inject(this)
        super.onCreate()
    }

    override val resourceVersion = "LoopStateTileService"
    override val source get() = loopStateSource
}


