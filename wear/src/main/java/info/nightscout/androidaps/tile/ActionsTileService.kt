package info.nightscout.androidaps.tile

class ActionsTileService : TileBase() {

    override val preferencePrefix = "tile_action_"
    override val resourceVersion = "1"
    override val idIconActionPrefix = "ic_action_"
    override val source = ActionSource

}
