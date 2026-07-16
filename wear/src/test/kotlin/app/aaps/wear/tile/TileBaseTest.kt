package app.aaps.wear.tile

import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.DeviceParametersBuilders.SCREEN_SHAPE_ROUND
import androidx.wear.protolayout.LayoutElementBuilders.Box
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.LayoutElementBuilders.Text
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.ResourceBuilders
import androidx.wear.tiles.TileBuilders
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.wear.AAPSLoggerTest
import app.aaps.wear.tile.source.ActionSource
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.ListenableFuture
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers the shared tile layout engine [TileBase] via the concrete [ActionsTileService]: onTileRequest across
 * every [WearControl] state and 0–4 action grid arrangement (which drives the button geometry, text sizing and
 * screen-shape branches), the freshness/resource-version wiring, and onResourcesRequest image mapping.
 *
 * Built via [Robolectric] so a Context is attached without running onCreate's Dagger injection; the `@Inject`
 * fields are set directly and the data source is mocked to drive each branch. onTileRequest runs its work on
 * Dispatchers.IO via a guava future, so `.get()` blocks for the built Tile.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
internal class TileBaseTest {

    private val prefs = mock<Preferences>()
    private val source = mock<ActionSource>()

    private fun service(): ActionsTileService =
        Robolectric.buildService(ActionsTileService::class.java).get().also {
            it.preferences = prefs
            it.aapsLogger = AAPSLoggerTest()
            it.actionSource = source
        }

    private fun action(text: String? = "Bolus", sub: String? = "1.0U") =
        Action(buttonText = text, buttonTextSub = sub, activityClass = "app.aaps.wear.Foo", iconRes = 0, action = null, message = null)

    private fun request(round: Boolean = true, widthDp: Int = 220, heightDp: Int = 220): RequestBuilders.TileRequest {
        // Only ROUND is a named constant here; leaving the shape unset (undefined) is != ROUND, so it drives the
        // non-round `else` branch of circleDiameter().
        val deviceParams = DeviceParameters.Builder()
            .setScreenWidthDp(widthDp)
            .setScreenHeightDp(heightDp)
            .apply { if (round) setScreenShape(SCREEN_SHAPE_ROUND) }
            .build()
        return RequestBuilders.TileRequest.Builder().setDeviceConfiguration(deviceParams).build()
    }

    private fun enabled() {
        whenever(prefs.getIfExists(BooleanKey.WearControl)).thenReturn(true)
        whenever(prefs.get(BooleanKey.WearControl)).thenReturn(true)
    }

    private fun disabled() {
        whenever(prefs.getIfExists(BooleanKey.WearControl)).thenReturn(false)
        whenever(prefs.get(BooleanKey.WearControl)).thenReturn(false)
    }

    private fun noData() = whenever(prefs.getIfExists(BooleanKey.WearControl)).thenReturn(null)

    // onTileRequest / onResourcesRequest are framework-only `protected` entry points; invoke them via reflection
    // so the full real code path runs without widening production visibility for a test.
    @Suppress("UNCHECKED_CAST")
    private fun TileBase.tileRequest(req: RequestBuilders.TileRequest): TileBuilders.Tile {
        val m = TileBase::class.java.getDeclaredMethod("onTileRequest", RequestBuilders.TileRequest::class.java).apply { isAccessible = true }
        return (m.invoke(this, req) as ListenableFuture<TileBuilders.Tile>).get()
    }

    @Suppress("UNCHECKED_CAST")
    private fun TileBase.resourcesRequest(req: RequestBuilders.ResourcesRequest): ResourceBuilders.Resources {
        val m = TileBase::class.java.getDeclaredMethod("onResourcesRequest", RequestBuilders.ResourcesRequest::class.java).apply { isAccessible = true }
        return (m.invoke(this, req) as ListenableFuture<ResourceBuilders.Resources>).get()
    }

    // The layout wraps the real content with a flavor header: a round screen overlays a curved Arc header
    // (Box[content, header]); a square screen stacks a flat Text header above it (Column[header, content]).
    // Unwrap so assertions target the content element itself, not the header wrapper.
    private fun contentOf(svc: ActionsTileService, req: RequestBuilders.TileRequest = request()): LayoutElement? =
        when (val root = svc.tileRequest(req).tileTimeline?.timelineEntries?.first()?.layout?.root) {
            is Box    -> root.contents.firstOrNull()
            is Column -> root.contents.getOrNull(1)
            else      -> root
        }

    // ---- WearControl states: each renders an explanatory Text, never buttons ----

    @Test fun `no data shows a text message`() {
        noData()
        whenever(source.getSelectedActions()).thenReturn(listOf(action()))
        whenever(source.getValidFor()).thenReturn(null)
        assertThat(contentOf(service())).isInstanceOf(Text::class.java)
    }

    @Test fun `disabled shows a text message`() {
        disabled()
        whenever(source.getSelectedActions()).thenReturn(listOf(action()))
        whenever(source.getValidFor()).thenReturn(null)
        assertThat(contentOf(service())).isInstanceOf(Text::class.java)
    }

    @Test fun `enabled with no actions shows the no-config text`() {
        enabled()
        whenever(source.getSelectedActions()).thenReturn(emptyList())
        whenever(source.getValidFor()).thenReturn(null)
        assertThat(contentOf(service())).isInstanceOf(Text::class.java)
    }

    // ---- grid arrangements: 1..4 actions all build a Column of buttons ----

    @Test fun `enabled with 1 to 4 actions builds a column of buttons`() {
        enabled()
        whenever(source.getValidFor()).thenReturn(null)
        for (n in 1..4) {
            whenever(source.getSelectedActions()).thenReturn((1..n).map { action() })
            assertThat(contentOf(service())).isInstanceOf(Column::class.java)
        }
    }

    @Test fun `square screen with icon-only and long-label actions still builds a column`() {
        enabled()
        whenever(source.getValidFor()).thenReturn(null)
        // icon-only (no text branch) + a >6 char label on a small square screen exercises the geometry/text-size branches
        whenever(source.getSelectedActions()).thenReturn(listOf(action(text = null, sub = null), action(text = "VeryLongLabel", sub = "sub")))
        assertThat(contentOf(service(), request(round = false, widthDp = 160, heightDp = 160))).isInstanceOf(Column::class.java)
    }

    // ---- freshness + resource-version wiring ----

    @Test fun `valid-for is written as the freshness interval and the resource version is set`() {
        enabled()
        whenever(source.getSelectedActions()).thenReturn(listOf(action()))
        whenever(source.getValidFor()).thenReturn(60_000L)
        val tile = service().tileRequest(request())
        assertThat(tile.freshnessIntervalMillis).isEqualTo(60_000L)
        assertThat(tile.resourcesVersion).isEqualTo("ActionsTileService")
    }

    @Test fun `null valid-for leaves the freshness interval unset`() {
        enabled()
        whenever(source.getSelectedActions()).thenReturn(listOf(action()))
        whenever(source.getValidFor()).thenReturn(null)
        assertThat(service().tileRequest(request()).freshnessIntervalMillis).isEqualTo(0L)
    }

    // ---- resources request ----

    @Test fun `resources request maps every source drawable id and carries the version`() {
        whenever(source.getResourceReferences(any())).thenReturn(listOf(111, 222))
        val res = service()
            .resourcesRequest(RequestBuilders.ResourcesRequest.Builder().setVersion("ActionsTileService").build())
        assertThat(res.version).isEqualTo("ActionsTileService")
        assertThat(res.idToImageMapping.keys).containsExactly("111", "222")
    }

    // ---- every concrete TileBase subclass wires its own source + resource version ----

    @Test fun `each tile service builds a tile carrying its own resource version`() {
        // All 6 subclasses are identical bar the @Inject source field + resourceVersion string; reflectively set a
        // mocked source of the field's concrete type so onTileRequest runs the real service.source getter.
        val serviceClasses = listOf(
            ActionsTileService::class.java,
            TempTargetTileService::class.java,
            UserActionTileService::class.java,
            RunningModeTileService::class.java,
            SceneTileService::class.java,
            QuickWizardTileService::class.java
        )
        for (cls in serviceClasses) {
            val svc = Robolectric.buildService(cls).get()
            svc.preferences = prefs
            svc.aapsLogger = AAPSLoggerTest()
            enabled()
            val sourceField = cls.declaredFields.first { TileSource::class.java.isAssignableFrom(it.type) }.apply { isAccessible = true }
            val src = Mockito.mock(sourceField.type) as TileSource
            whenever(src.getSelectedActions()).thenReturn(emptyList())
            whenever(src.getValidFor()).thenReturn(null)
            sourceField.set(svc, src)

            val tile = svc.tileRequest(request())

            assertThat(tile.resourcesVersion).isEqualTo(svc.resourceVersion)
        }
    }
}
