package app.aaps.wear.tile

import app.aaps.wear.tile.source.QuickWizardSource
import dagger.android.AndroidInjection
import javax.inject.Inject

class QuickWizardTileService : TileBase() {

    @Inject lateinit var quickWizardSource: QuickWizardSource

    // Not derived from DaggerService, do injection here
    override fun onCreate() {
        AndroidInjection.inject(this)
        super.onCreate()
    }

    override val resourceVersion = "QuickWizardTileService"
    override val source get() = quickWizardSource
}
