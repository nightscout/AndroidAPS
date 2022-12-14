package info.nightscout.androidaps.implementations

import dagger.Reusable
import dagger.android.HasAndroidInjector
import info.nightscout.implementation.profile.ProfileStoreObject
import info.nightscout.interfaces.aps.APSResult
import info.nightscout.interfaces.aps.AutosensData
import info.nightscout.interfaces.profile.Instantiator
import info.nightscout.interfaces.profile.ProfileStore
import info.nightscout.plugins.aps.APSResultObject
import info.nightscout.plugins.iob.iobCobCalculator.data.AutosensDataObject
import info.nightscout.shared.utils.DateUtil
import org.json.JSONObject
import javax.inject.Inject

@Reusable
class InstantiatorImpl @Inject constructor(
    private val injector: HasAndroidInjector,
    private val dateUtil: DateUtil
) : Instantiator {

    override fun provideProfileStore(jsonObject: JSONObject): ProfileStore =
        ProfileStoreObject(injector, jsonObject, dateUtil)

    override fun provideAPSResultObject(): APSResult = APSResultObject(injector)

    override fun provideAutosensDataObject(): AutosensData = AutosensDataObject(injector)
}