package info.nightscout.androidaps.tile

import dagger.android.AndroidInjection
import javax.inject.Inject

class ActionsTileService : TileBase() {

    @Inject lateinit var actionSource: ActionSource

    // Not derived from DaggerService, do injection here
    override fun onCreate() {
        AndroidInjection.inject(this)
        super.onCreate()
    }

    override val resourceVersion = "ActionsTileService"
    override val source get() = actionSource
}
