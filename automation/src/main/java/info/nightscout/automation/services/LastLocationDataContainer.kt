package info.nightscout.automation.services

import android.location.Location
import info.nightscout.androidaps.annotations.OpenForTesting
import javax.inject.Inject
import javax.inject.Singleton

@OpenForTesting
@Singleton
class LastLocationDataContainer @Inject constructor() {

    var lastLocation: Location? = null
}