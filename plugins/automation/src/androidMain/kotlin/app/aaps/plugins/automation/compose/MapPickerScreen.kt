package app.aaps.plugins.automation.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.aaps.plugins.automation.R
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker

@Composable
fun MapPickerScreen(
    initialLat: Double?,
    initialLon: Double?,
    onLocationTapped: (Double, Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val initialLocation = remember(initialLat, initialLon) {
        if (initialLat != null && initialLon != null) GeoPoint(initialLat, initialLon) else null
    }
    var selectedCoordinates by remember { mutableStateOf<Pair<Double, Double>?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            OsmdroidMapView(
                initialLocation = initialLocation,
                onLocationSelected = { lat, lon ->
                    selectedCoordinates = lat to lon
                    onLocationTapped(lat, lon)
                }
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = selectedCoordinates?.let { (lat, lon) ->
                    stringResource(R.string.selected_coords, lat, lon)
                } ?: stringResource(R.string.tap_to_select_location),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

@Composable
private fun OsmdroidMapView(
    initialLocation: GeoPoint?,
    onLocationSelected: (Double, Double) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnLocationSelected by rememberUpdatedState(onLocationSelected)
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var selectedMarker: Marker? = remember { null }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView?.onResume()
                Lifecycle.Event.ON_PAUSE  -> mapView?.onPause()

                else                      -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            Configuration.getInstance().userAgentValue = "AAPS/1.0"

            MapView(context).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(15.0)

                val startPoint = initialLocation ?: GeoPoint(51.5074, -0.1278)
                controller.setCenter(startPoint)

                initialLocation?.let { point ->
                    val currentMarker = Marker(this)
                    currentMarker.position = point
                    currentMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    currentMarker.title = "Current Location"
                    currentMarker.icon = ContextCompat.getDrawable(context, R.drawable.ic_my_location)
                    overlays.add(currentMarker)
                }

                val mapViewRef = this
                val mapEventsReceiver = object : MapEventsReceiver {
                    override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                        if (selectedMarker == null) {
                            selectedMarker = Marker(mapViewRef).apply {
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            }
                            mapViewRef.overlays.add(selectedMarker)
                        }
                        selectedMarker.position = p
                        selectedMarker.title = "Selected Location"
                        mapViewRef.invalidate()

                        currentOnLocationSelected(p.latitude, p.longitude)
                        return true
                    }

                    override fun longPressHelper(p: GeoPoint): Boolean = false
                }

                val eventsOverlay = MapEventsOverlay(mapEventsReceiver)
                overlays.add(0, eventsOverlay)

                mapView = this
            }
        }
    )
}
