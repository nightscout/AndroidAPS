package app.aaps.implementations

import app.aaps.core.interfaces.aps.APSResult
import app.aaps.core.interfaces.aps.AutosensData
import app.aaps.core.interfaces.objects.Instantiator
import app.aaps.core.interfaces.profile.ProfileStore
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.implementation.profile.ProfileStoreObject
import app.aaps.plugins.aps.APSResultObject
import dagger.Reusable
import dagger.android.HasAndroidInjector
import info.nightscout.plugins.iob.iobCobCalculator.data.AutosensDataObject
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