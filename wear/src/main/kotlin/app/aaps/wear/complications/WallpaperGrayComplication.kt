package app.aaps.wear.complications

/*
 * Created by dlvoy on 2019-11-12
 */
class WallpaperGrayComplication : WallpaperComplication() {

    override val wallpaperAssetsFileName: String = "watch_gray.jpg"
    override fun getProviderCanonicalName(): String = WallpaperGrayComplication::class.java.canonicalName!!
    override fun getComplicationAction(): ComplicationAction = ComplicationAction.NONE
}