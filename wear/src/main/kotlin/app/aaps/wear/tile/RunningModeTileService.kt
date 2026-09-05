package app.aaps.wear.tile

import androidx.wear.tiles.EventBuilders.TileEnterEvent
import app.aaps.core.interfaces.di.injectMetroMembers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventWearToMobile
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.wear.di.WearMetroService
import app.aaps.wear.tile.source.RunningModeSource
import dev.zacsweers.metro.Inject

class RunningModeTileService : TileBase() {

    @Inject lateinit var runningModeSource: RunningModeSource
    @Inject lateinit var rxBus: RxBus

    // Not derived from WearMetroService, do injection here
    override fun onCreate() {
        injectMetroMembers(this)
        super.onCreate()
    }

    override val resourceVersion = "RunningModeTileService"
    override val source get() = runningModeSource

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onTileEnterEvent(requestParams: TileEnterEvent) {
        rxBus.send(EventWearToMobile(EventData.RunningModeRequest(System.currentTimeMillis())))
    }
}


