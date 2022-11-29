package info.nightscout.implementation.profile

import dagger.android.HasAndroidInjector
import info.nightscout.interfaces.profile.ProfileInstantiator
import info.nightscout.interfaces.profile.ProfileStore
import info.nightscout.shared.utils.DateUtil
import org.json.JSONObject
import javax.inject.Inject

class ProfileInstantiatorImpl @Inject constructor(
    private val injector: HasAndroidInjector,
    private val dateUtil: DateUtil
): ProfileInstantiator {

    override fun storeInstance(jsonObject: JSONObject): ProfileStore =
        ProfileStoreObject(injector, jsonObject, dateUtil)
}