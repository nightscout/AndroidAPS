package app.aaps.wear.complications

/**
 * Gray Wallpaper Complication
 *
 * Displays gray-themed wallpaper image
 * Asset: watch_gray.jpg
 *
 */
class WallpaperGrayComplication : WallpaperComplication() {

    override val wallpaperAssetsFileName: String = "watch_gray.jpg"
    override fun getProviderCanonicalName(): String = WallpaperGrayComplication::class.java.canonicalName!!
}