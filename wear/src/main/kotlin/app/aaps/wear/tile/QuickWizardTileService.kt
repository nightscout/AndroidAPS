package app.aaps.wear.tile

import androidx.wear.tiles.EventBuilders.TileEnterEvent
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventWearToMobile
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.wear.tile.source.QuickWizardSource
import dagger.android.AndroidInjection
import javax.inject.Inject

class QuickWizardTileService : TileBase() {

    @Inject lateinit var quickWizardSource: QuickWizardSource
    @Inject lateinit var rxBus: RxBus

    // Not derived from DaggerService, do injection here
    override fun onCreate() {
        AndroidInjection.inject(this)
        super.onCreate()
    }

    override val resourceVersion = "QuickWizardTileService"
    override val source get() = quickWizardSource

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onTileEnterEvent(requestParams: TileEnterEvent) {
        rxBus.send(EventWearToMobile(EventData.ActionResendData("QuickWizardTileService")))
    }
}
