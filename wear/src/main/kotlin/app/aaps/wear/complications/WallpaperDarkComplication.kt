package app.aaps.wear.complications

/*
 * Created by dlvoy on 2019-11-12
 */
class WallpaperDarkComplication : WallpaperComplication() {

    override val wallpaperAssetsFileName: String = "watch_dark.jpg"
    override fun getProviderCanonicalName(): String = WallpaperDarkComplication::class.java.canonicalName!!
    override fun getComplicationAction(): ComplicationAction = ComplicationAction.NONE
}