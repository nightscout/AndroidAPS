package info.nightscout.androidaps.historyBrowser

import android.content.Context
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload
import info.nightscout.androidaps.plugins.general.nsclient.UploadQueue
import info.nightscout.androidaps.plugins.treatments.TreatmentService
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TreatmentsPluginHistory @Inject constructor(
    injector: HasAndroidInjector,
    aapsLogger: AAPSLogger,
    rxBus: RxBusWrapper,
    resourceHelper: ResourceHelper,
    context: Context,
    sp: SP,
    profileFunction: ProfileFunction,
    activePlugin: ActivePluginProvider,
    nsUpload: NSUpload,
    fabricPrivacy: FabricPrivacy,
    dateUtil: DateUtil,
    uploadQueue: UploadQueue
) : TreatmentsPlugin(injector, aapsLogger, rxBus, resourceHelper, context, sp, profileFunction, activePlugin, nsUpload, fabricPrivacy, dateUtil, uploadQueue) {

    init {
        onStart()
    }

    override fun onStart() {
        service = TreatmentService(injector)
        initializeData(range())
    }
}