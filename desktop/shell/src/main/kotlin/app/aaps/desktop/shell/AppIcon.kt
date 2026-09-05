package app.aaps.desktop.shell

import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import app.aaps.core.interfaces.configuration.Config
import java.awt.Image
import javax.imageio.ImageIO

/**
 * Which launcher icon this build shows.
 *
 * The same rule `IconsProviderImplementation` applies on Android, said again here because the icon
 * is what tells a user which app they are looking at. A follower running beside a master must not
 * wear the master's icon, so the choice follows [Config] rather than being fixed.
 *
 * The files are copied out of `core/ui` by `copyDesktopAppIcon`, so the desktop draws the same
 * artwork as the phone instead of a second copy that can drift.
 */
internal fun appIconResource(config: Config): String = "icons/" + when {
    config.AAPSCLIENT3 -> "ic_greenowl"
    config.AAPSCLIENT2 -> "ic_blueowl"
    config.AAPSCLIENT1 -> "ic_yellowowl"
    config.PUMPCONTROL -> "ic_pumpcontrol"
    else               -> "ic_launcher"
} + ".png"

/**
 * What the window wears when the graph could not be built and there is no config to ask.
 *
 * The main icon and the main name, because a build that cannot say what it is is not a client.
 */
internal const val DEFAULT_APP_ICON: String = "icons/ic_launcher.png"

/** @see DEFAULT_APP_ICON */
internal const val DEFAULT_APP_NAME: String = "AAPS"

/** Only here to name the class loader that holds the copied icons. */
private object IconResources

/**
 * Reads one icon off the classpath.
 *
 * Read and decoded by hand rather than with `painterResource`, which Compose deprecated for
 * classpath resources in favour of the compose-resources plugin. That plugin is not used here - see
 * the string generation for why - and `decodeToImageBitmap` is the supported way to turn bytes into
 * an image on every platform, so iOS reads its icon exactly the same way.
 *
 * Loaded once at startup and shared by the window, the drawer and the About dialog. Failing loudly
 * is right: the file is copied in by the build, so a missing one means the build is wrong, and a
 * window with no icon would be a quiet way to ship that.
 */
internal fun loadAppIcon(path: String): Painter {
    val loader = IconResources::class.java.classLoader
    val bytes = checkNotNull(loader?.getResourceAsStream(path)) { "Icon $path is missing from the classpath" }
        .use { it.readBytes() }
    return BitmapPainter(bytes.decodeToImageBitmap())
}

/**
 * The same icon again, as AWT understands it.
 *
 * The system tray is AWT, not Compose, so it cannot take the [Painter] the window uses. Reading the
 * bytes twice is cheaper than the alternatives and happens once at startup.
 *
 * Null rather than throwing: a tray icon is a nicety, and a machine with no tray at all already
 * makes do without one.
 */
internal fun loadAwtAppIcon(path: String): Image? = runCatching {
    IconResources::class.java.classLoader?.getResourceAsStream(path)?.use { ImageIO.read(it) }
}.getOrNull()
