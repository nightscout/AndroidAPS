package app.aaps.plugins.automation.services

import android.location.Location
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.SingleIn
import javax.inject.Inject

@SingleIn(AppScope::class)
class LastLocationDataContainer @Inject constructor() {

    var lastLocation: Location? = null
}