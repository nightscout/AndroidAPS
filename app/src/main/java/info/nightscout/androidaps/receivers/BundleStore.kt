package info.nightscout.androidaps.receivers

import android.os.Bundle
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BundleStore @Inject constructor() {
    private val store = HashMap<Long, Bundle>()
    private var counter = 0L

    @Synchronized
    fun store(bundle: Bundle) : Long {
        store.put(counter, bundle)
        return counter++
    }

    @Synchronized
    fun pickup(key: Long) : Bundle? {
        val bundle = store[key]
        store.remove(key)
        return bundle
    }
}