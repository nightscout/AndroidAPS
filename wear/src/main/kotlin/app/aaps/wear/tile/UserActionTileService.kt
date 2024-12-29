package app.aaps.wear.tile

import app.aaps.wear.tile.source.UserActionSource
import dagger.android.AndroidInjection
import javax.inject.Inject

class UserActionTileService : TileBase() {

    @Inject lateinit var userActionSource: UserActionSource

    // Not derived from DaggerService, do injection here
    override fun onCreate() {
        AndroidInjection.inject(this)
        super.onCreate()
    }

    override val resourceVersion = "UserActionTileService"
    override val source get() = userActionSource
}
