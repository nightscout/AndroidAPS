package app.aaps.wear.complications

/**
 * Dark Wallpaper Complication
 *
 * Displays dark-themed wallpaper image
 * Asset: watch_dark.jpg
 *
 */
class WallpaperDarkComplication : WallpaperComplication() {

    override val wallpaperAssetsFileName: String = "watch_dark.jpg"
    override fun getProviderCanonicalName(): String = WallpaperDarkComplication::class.java.canonicalName!!
}