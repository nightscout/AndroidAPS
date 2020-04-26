package info.nightscout.androidaps.services

import android.location.Location
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Created by adrian on 2020-01-06.
 */

@Singleton
class LastLocationDataContainer @Inject constructor() {
    var lastLocation: Location? = null
}