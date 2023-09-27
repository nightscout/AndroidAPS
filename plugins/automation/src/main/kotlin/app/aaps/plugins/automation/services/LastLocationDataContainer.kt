package app.aaps.plugins.automation.services

import android.location.Location
import app.aaps.annotations.OpenForTesting
import javax.inject.Inject
import javax.inject.Singleton

@OpenForTesting
@Singleton
class LastLocationDataContainer @Inject constructor() {

    var lastLocation: Location? = null
}