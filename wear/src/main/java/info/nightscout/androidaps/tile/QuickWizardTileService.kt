package info.nightscout.androidaps.tile

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
