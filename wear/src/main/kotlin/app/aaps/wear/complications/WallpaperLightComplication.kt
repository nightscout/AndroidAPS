package app.aaps.wear.complications

/**
 * Light Wallpaper Complication
 *
 * Displays light-themed wallpaper image
 * Asset: watch_light.jpg
 *
 */
class WallpaperLightComplication : WallpaperComplication() {

    override val wallpaperAssetsFileName: String = "watch_light.jpg"
    override fun getProviderCanonicalName(): String = WallpaperLightComplication::class.java.canonicalName!!
}