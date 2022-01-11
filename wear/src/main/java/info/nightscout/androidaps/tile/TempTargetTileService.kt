package info.nightscout.androidaps.tile

class TempTargetTileService : TileBase() {

    override val preferencePrefix = "tile_tempt_"
    override val resourceVersion = "1"
    override val idIconActionPrefix = "ic_tempt_"
    override val source = TempTargetSource;

}
