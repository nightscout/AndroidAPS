package info.nightscout.androidaps.tile

const val TAG = "QuickWizard"

class QuickWizardTileService : TileBase() {
    override val resourceVersion = "QuickWizardTileService"
    override val source = QuickWizardSource
}
