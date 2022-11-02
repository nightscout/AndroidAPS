package info.nightscout.androidaps.implementations

import info.nightscout.androidaps.MainActivity
import info.nightscout.androidaps.interfaces.ActivityNames
import javax.inject.Inject

class ActivityNamesImpl @Inject constructor() : ActivityNames {

    override val mainActivityClass: Class<*>
        get() = MainActivity::class.java
}