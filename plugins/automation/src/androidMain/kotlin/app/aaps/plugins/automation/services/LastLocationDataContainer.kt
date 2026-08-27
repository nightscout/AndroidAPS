package app.aaps.plugins.automation.services

import android.location.Location
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@SingleIn(AppScope::class)
class LastLocationDataContainer @Inject constructor() {

    var lastLocation: Location? = null
}