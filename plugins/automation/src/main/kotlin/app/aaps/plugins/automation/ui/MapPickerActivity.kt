package app.aaps.plugins.automation.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.AapsTopAppBar
import app.aaps.core.ui.compose.LocalPreferences
import app.aaps.core.ui.locale.LocaleHelper
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.events.EventPlaceSelected
import app.aaps.plugins.automation.services.LastLocationDataContainer
import dagger.android.support.DaggerAppCompatActivity
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import javax.inject.Inject

class MapPickerActivity : DaggerAppCompatActivity() {

    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var locationDataContainer: LastLocationDataContainer
    @Inject lateinit var preferences: Preferences

    private var selectedLat: Double? = null
    private var selectedLon: Double? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val initialLat = intent.getDoubleExtra(EXTRA_LATITUDE, Double.NaN)
        val initialLon = intent.getDoubleExtra(EXTRA_LONGITUDE, Double.NaN)
        val initialLocation = if (!initialLat.isNaN() && !initialLon.isNaN()) {
            GeoPoint(initialLat, initialLon)
        } else {
            locationDataContainer.lastLocation?.let {
                GeoPoint(it.latitude, it.longitude)
            }
        }

        setContent {
            CompositionLocalProvider(
                LocalPreferences provides preferences
            ) {
                AapsTheme {
                    MapPickerScreen(
                        initialLocation = initialLocation,
                        onLocationSelected = { lat, lon ->
                            selectedLat = lat
                            selectedLon = lon
                        },
                        onConfirm = {
                            val lat = selectedLat
                            val lon = selectedLon
                            if (lat != null && lon != null) {
                                rxBus.send(EventPlaceSelected(lat, lon))
                            }
                            finish()
                        },
                        onCancel = {
                            finish()
                        }
                    )
                }
            }
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    companion object {

        private const val EXTRA_LATITUDE = "latitude"
        private const val EXTRA_LONGITUDE = "longitude"

        fun createIntent(context: Context, latitude: Double? = null, longitude: Double? = null): Intent {
            return Intent(context, MapPickerActivity::class.java).apply {
                latitude?.let { putExtra(EXTRA_LATITUDE, it) }
                longitude?.let { putExtra(EXTRA_LONGITUDE, it) }
            }
        }
    }
}

@Composable
private fun MapPickerScreen(
    initialLocation: GeoPoint?,
    onLocationSelected: (Double, Double) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    var selectedCoordinates by remember { mutableStateOf<Pair<Double, Double>?>(null) }

    Scaffold(
        topBar = {
            AapsTopAppBar(
                title = { Text(stringResource(R.string.pick_from_map)) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(app.aaps.core.ui.R.string.cancel)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onConfirm,
                        enabled = selectedCoordinates != null
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = stringResource(app.aaps.core.ui.R.string.ok),
                            tint = if (selectedCoordinates != null)
                                MaterialTheme.colorScheme.onSurface
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Map takes all available space
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                OsmdroidMapView(
                    initialLocation = initialLocation,
                    onLocationSelected = { lat, lon ->
                        selectedCoordinates = lat to lon
                        onLocationSelected(lat, lon)
                    }
                )
            }

            // Coordinates display at bottom
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
            org.osmdroid.config.Configuration.getInstance().userAgentValue = "AAPS/1.0"

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
