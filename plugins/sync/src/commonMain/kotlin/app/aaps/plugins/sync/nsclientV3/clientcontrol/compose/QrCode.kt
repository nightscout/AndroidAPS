package app.aaps.plugins.sync.nsclientV3.clientcontrol.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import kotlin.math.floor

/** The squares of a QR code: [size] x [size], true for a dark one, without the empty border around it. */
internal class QrModules(val size: Int, private val dark: BooleanArray) {

    operator fun get(x: Int, y: Int): Boolean = dark[y * size + x]
}

/** [text] as a QR code, or null where this platform cannot make one (iOS for now: it is only a client). */
internal expect fun qrModules(text: String): QrModules?

/** True where this platform can show a QR code. Asked by making a tiny one, so it cannot disagree with [qrModules]. */
internal val qrCodeAvailable: Boolean by lazy { qrModules("AAPS") != null }

/**
 * The camera picture, calling [onScanned] with the text of every QR code it sees (the same code many times). Asks for
 * the camera permission itself and shows why when it is refused.
 */
@Composable
internal expect fun QrScanner(onScanned: (String) -> Unit, modifier: Modifier = Modifier)

/** Empty squares a scanner needs around the code to find it. */
private const val QUIET_ZONE = 4

/**
 * Draws [modules] black on white, square, with the empty border scanners need. Fixed colours, not theme colours: a
 * camera needs dark squares on a light ground, in the dark theme too.
 */
@Composable
internal fun QrCodeImage(modules: QrModules, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.aspectRatio(1f)) {
        val count = modules.size + 2 * QUIET_ZONE
        // Whole pixels per square: with a fraction the squares get thin light lines between them.
        val cell = floor(size.minDimension / count).coerceAtLeast(1f)
        val start = Offset((size.width - cell * count) / 2f + cell * QUIET_ZONE, (size.height - cell * count) / 2f + cell * QUIET_ZONE)
        drawRect(Color.White)
        for (y in 0 until modules.size) {
            var x = 0
            while (x < modules.size) {
                if (!modules[x, y]) {
                    x++
                    continue
                }
                // One rectangle for each run of dark squares in a row.
                val runStart = x
                while (x < modules.size && modules[x, y]) x++
                drawRect(
                    color = Color.Black,
                    topLeft = Offset(start.x + runStart * cell, start.y + y * cell),
                    size = Size((x - runStart) * cell, cell)
                )
            }
        }
    }
}
