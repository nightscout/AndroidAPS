package info.nightscout.plugins.sync.nsclientV3.workers

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.android.HasAndroidInjector
import info.nightscout.core.utils.receivers.DataWorkerStorage
import info.nightscout.database.impl.AppRepository
import info.nightscout.interfaces.Config
import info.nightscout.interfaces.Constants
import info.nightscout.interfaces.XDripBroadcast
import info.nightscout.interfaces.logging.UserEntryLogger
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.plugins.sync.R
import info.nightscout.interfaces.nsclient.StoreDataForDb
import info.nightscout.plugins.sync.nsShared.StoreDataForDbImpl
import info.nightscout.plugins.sync.nsclientV3.extensions.toBolus
import info.nightscout.plugins.sync.nsclientV3.extensions.toBolusCalculatorResult
import info.nightscout.plugins.sync.nsclientV3.extensions.toCarbs
import info.nightscout.plugins.sync.nsclientV3.extensions.toEffectiveProfileSwitch
import info.nightscout.plugins.sync.nsclientV3.extensions.toExtendedBolus
import info.nightscout.plugins.sync.nsclientV3.extensions.toOfflineEvent
import info.nightscout.plugins.sync.nsclientV3.extensions.toProfileSwitch
import info.nightscout.plugins.sync.nsclientV3.extensions.toTemporaryBasal
import info.nightscout.plugins.sync.nsclientV3.extensions.toTemporaryTarget
import info.nightscout.plugins.sync.nsclientV3.extensions.toTherapyEvent
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.sdk.localmodel.treatment.NSBolus
import info.nightscout.sdk.localmodel.treatment.NSBolusWizard
import info.nightscout.sdk.localmodel.treatment.NSCarbs
import info.nightscout.sdk.localmodel.treatment.NSEffectiveProfileSwitch
import info.nightscout.sdk.localmodel.treatment.NSExtendedBolus
import info.nightscout.sdk.localmodel.treatment.NSOfflineEvent
import info.nightscout.sdk.localmodel.treatment.NSProfileSwitch
import info.nightscout.sdk.localmodel.treatment.NSTemporaryBasal
import info.nightscout.sdk.localmodel.treatment.NSTemporaryTarget
import info.nightscout.sdk.localmodel.treatment.NSTherapyEvent
import info.nightscout.sdk.localmodel.treatment.NSTreatment
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.DateUtil
import javax.inject.Inject

class ProcessTreatmentsWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    @Inject lateinit var dataWorkerStorage: DataWorkerStorage
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var config: Config
    @Inject lateinit var sp: SP
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var repository: AppRepository
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var xDripBroadcast: XDripBroadcast
    @Inject lateinit var storeDataForDb: StoreDataForDbImpl

    override fun doWork(): Result {
        @Suppress("UNCHECKED_CAST")
        val treatments = dataWorkerStorage.pickupObject(inputData.getLong(DataWorkerStorage.STORE_KEY, -1)) as List<NSTreatment>?
            ?: return Result.failure(workDataOf("Error" to "missing input data"))

        val ret = Result.success()
        var latestDateInReceivedData = 0L

        for (treatment in treatments) {
            aapsLogger.debug(LTag.DATABASE, "Received NS treatment: $treatment")

            //Find latest date in treatment
            val mills = treatment.date
            if (mills != 0L && mills < dateUtil.now())
                if (mills > latestDateInReceivedData) latestDateInReceivedData = mills

            when (treatment) {
                is NSBolus                  ->
                    if (sp.getBoolean(R.string.key_ns_receive_insulin, false) || config.NSCLIENT)
                        storeDataForDb.boluses.add(treatment.toBolus())

                is NSCarbs                  ->
                    if (sp.getBoolean(R.string.key_ns_receive_carbs, false) || config.NSCLIENT)
                        storeDataForDb.carbs.add(treatment.toCarbs())

                is NSTemporaryTarget        ->
                    if (sp.getBoolean(R.string.key_ns_receive_temp_target, false) || config.NSCLIENT) {
                        if (treatment.duration > 0L) {
                            // not ending event
                            if (treatment.targetBottomAsMgdl() < Constants.MIN_TT_MGDL
                                || treatment.targetBottomAsMgdl() > Constants.MAX_TT_MGDL
                                || treatment.targetTopAsMgdl() < Constants.MIN_TT_MGDL
                                || treatment.targetTopAsMgdl() > Constants.MAX_TT_MGDL
                                || treatment.targetBottomAsMgdl() > treatment.targetTopAsMgdl()
                            ) {
                                aapsLogger.debug(LTag.DATABASE, "Ignored TemporaryTarget $treatment")
                                continue
                            }
                        }
                        storeDataForDb.temporaryTargets.add(treatment.toTemporaryTarget())
                    }

                is NSTemporaryBasal         ->
                    if (config.isEngineeringMode() && sp.getBoolean(R.string.key_ns_receive_tbr_eb, false) || config.NSCLIENT)
                        storeDataForDb.temporaryBasals.add(treatment.toTemporaryBasal())

                is NSEffectiveProfileSwitch ->
                    if (sp.getBoolean(R.string.key_ns_receive_profile_switch, false) || config.NSCLIENT) {
                        treatment.toEffectiveProfileSwitch(dateUtil)?.let { effectiveProfileSwitch ->
                            storeDataForDb.effectiveProfileSwitches.add(effectiveProfileSwitch)
                        }
                    }

                is NSProfileSwitch          ->
                    if (sp.getBoolean(R.string.key_ns_receive_profile_switch, false) || config.NSCLIENT) {
                        treatment.toProfileSwitch(activePlugin, dateUtil)?.let { profileSwitch ->
                            storeDataForDb.profileSwitches.add(profileSwitch)
                        }
                    }

                is NSBolusWizard            ->
                    treatment.toBolusCalculatorResult()?.let { bolusCalculatorResult ->
                        storeDataForDb.bolusCalculatorResults.add(bolusCalculatorResult)
                    }

                is NSTherapyEvent           ->
                    if (sp.getBoolean(R.string.key_ns_receive_therapy_events, false) || config.NSCLIENT)
                        treatment.toTherapyEvent().let { therapyEvent ->
                            storeDataForDb.therapyEvents.add(therapyEvent)
                        }

                is NSOfflineEvent           ->
                    if (sp.getBoolean(R.string.key_ns_receive_offline_event, false) && config.isEngineeringMode() || config.NSCLIENT)
                        treatment.toOfflineEvent().let { offlineEvent ->
                            storeDataForDb.offlineEvents.add(offlineEvent)
                        }

                is NSExtendedBolus          ->
                    if (config.isEngineeringMode() && sp.getBoolean(R.string.key_ns_receive_tbr_eb, false) || config.NSCLIENT)
                        treatment.toExtendedBolus().let { extendedBolus ->
                            storeDataForDb.extendedBoluses.add(extendedBolus)
                        }
            }
        }
        activePlugin.activeNsClient?.updateLatestTreatmentReceivedIfNewer(latestDateInReceivedData)
//        xDripBroadcast.sendTreatments(treatments)
        return ret
    }

    init {
        (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)
    }
}