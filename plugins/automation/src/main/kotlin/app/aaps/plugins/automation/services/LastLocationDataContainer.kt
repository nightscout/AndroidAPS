package app.aaps.plugins.automation.services

import android.location.Location
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LastLocationDataContainer @Inject constructor() {

    var lastLocation: Location? = null
}