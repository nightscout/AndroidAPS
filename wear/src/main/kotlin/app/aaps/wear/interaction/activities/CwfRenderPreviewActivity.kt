package app.aaps.wear.interaction.activities

import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import app.aaps.core.interfaces.di.injectMetroMembers
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.wear.watchfaces.CustomWatchface
import dev.zacsweers.metro.HasMemberInjections
import dev.zacsweers.metro.Inject

/**
 * Proof of concept, not a user-facing screen.
 *
 * Draws the currently loaded Custom watch face into a `Bitmap` through
 * [app.aaps.wear.watchfaces.utils.BaseWatchFace.renderToBitmap] and shows the result, with no watch
 * face, no complication and no Watch Face Push involved. It exists to answer one question on a real
 * watch: can the Custom watch face be drawn by an instance the system never started?
 *
 * That is the assumption the whole "Custom Watchface in WFF" plan rests on - see
 * `_docs/CWF_WFF_Prompt.md`, sections 7d and Phase 2 (POC 1). On watches whose firmware no longer
 * binds code-based watch faces, the drawing has to be published through an image complication
 * instead, and the provider must be able to produce it exactly this way.
 *
 * Not in any menu on purpose. Start it with:
 * `adb shell am start -n <wear app id>/app.aaps.wear.interaction.activities.CwfRenderPreviewActivity`
 *
 * Tap the screen to render again. Any failure is shown on screen rather than crashing, because the
 * failure is the interesting result.
 *
 * Texts are English literals rather than string resources: this screen is a temporary POC and must
 * not add strings for translators.
 */
@HasMemberInjections
class CwfRenderPreviewActivity : AppCompatActivity() {

    @Inject lateinit var aapsLogger: AAPSLogger

    override fun onCreate(savedInstanceState: Bundle?) {
        injectMetroMembers(this)
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { CwfRenderPreviewScreen(::render) } }
    }

    /**
     * One instance kept **warm** across renders, so a measurement shows the cost of drawing alone -
     * no construction, no injection, no inflation. That is the cost the split-render design depends
     * on, and it is what a real provider keeping an instance would pay.
     */
    private val warmWatchFace by lazy {
        CustomWatchface().also { it.prepareForRendering(this) }
    }

    /**
     * Times a warm render of the whole face and of each layer, and returns the whole picture.
     *
     * Data is refreshed before the render, as a real warm provider would have to - otherwise the
     * measurement would flatter itself by skipping the repository read.
     */
    private fun render(): Result<Bitmap> = runCatching {
        val metrics = resources.displayMetrics
        val w = metrics.widthPixels
        val h = metrics.heightPixels

        warmWatchFace.refreshRenderData()
        val started = System.currentTimeMillis()
        val whole = warmWatchFace.renderToBitmap(w, h)
        val wholeMs = System.currentTimeMillis() - started

        val perLayer = CustomWatchface.RenderLayer.entries.joinToString(" ") { layer ->
            val layerStarted = System.currentTimeMillis()
            warmWatchFace.renderLayer(w, h, layer).recycle()
            "${layer.name}=${System.currentTimeMillis() - layerStarted}ms"
        }

        aapsLogger.debug(LTag.WEAR, "CwfRenderPreview: warm ${w}x$h whole=${wholeMs}ms $perLayer")
        whole
    }.onFailure { aapsLogger.error(LTag.WEAR, "CwfRenderPreview: render failed", it) }
}

@Composable
private fun CwfRenderPreviewScreen(render: () -> Result<Bitmap>) {
    var result by remember { mutableStateOf(render()) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { result = render() },
        contentAlignment = Alignment.Center
    ) {
        result.fold(
            onSuccess = { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Custom watch face rendered to a bitmap",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            },
            onFailure = { error ->
                Text(
                    text = "Render failed\n${error::class.java.simpleName}\n${error.message ?: ""}",
                    color = Color.Red,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(12.dp)
                )
            }
        )
    }
}
