package app.aaps.wear.tile

import androidx.wear.tiles.EventBuilders.TileEnterEvent
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventWearToMobile
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.wear.tile.source.RunningModeSource
import dagger.android.AndroidInjection
import javax.inject.Inject

class RunningModeTileService : TileBase() {

    @Inject lateinit var runningModeSource: RunningModeSource
    @Inject lateinit var rxBus: RxBus

    // Not derived from DaggerService, do injection here
    override fun onCreate() {
        AndroidInjection.inject(this)
        super.onCreate()
    }

    override val resourceVersion = "RunningModeTileService"
    override val source get() = runningModeSource

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onTileEnterEvent(requestParams: TileEnterEvent) {
        rxBus.send(EventWearToMobile(EventData.RunningModeRequest(System.currentTimeMillis())))
    }
}


