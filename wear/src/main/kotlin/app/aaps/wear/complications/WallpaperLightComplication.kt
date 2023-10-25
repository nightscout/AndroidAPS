package app.aaps.wear.complications

/*
 * Created by dlvoy on 2019-11-12
 */
class WallpaperLightComplication : WallpaperComplication() {

    override val wallpaperAssetsFileName: String = "watch_light.jpg"
    override fun getProviderCanonicalName(): String = WallpaperLightComplication::class.java.canonicalName!!
    override fun getComplicationAction(): ComplicationAction = ComplicationAction.NONE
}