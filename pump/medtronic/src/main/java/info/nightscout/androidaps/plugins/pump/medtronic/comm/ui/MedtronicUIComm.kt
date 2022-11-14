package info.nightscout.androidaps.plugins.pump.medtronic.comm.ui

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.plugins.pump.medtronic.comm.MedtronicCommunicationManager
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedtronicCommandType
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedtronicUtil
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import javax.inject.Inject

/**
 * Created by andy on 6/14/18.
 */
class MedtronicUIComm @Inject constructor(
    private val injector: HasAndroidInjector,
    private val aapsLogger: AAPSLogger,
    private val medtronicUtil: MedtronicUtil,
    private val medtronicUIPostprocessor: MedtronicUIPostprocessor,
    private val medtronicCommunicationManager: MedtronicCommunicationManager
) {

    fun executeCommand(commandType: MedtronicCommandType): MedtronicUITask {
        return executeCommand(commandType, null)
    }

    @Synchronized
    fun executeCommand(commandType: MedtronicCommandType, parameters: ArrayList<Any>?): MedtronicUITask {

        aapsLogger.info(LTag.PUMP, "Execute Command: " + commandType.name)
        val task = MedtronicUITask(injector, commandType, parameters)
        medtronicUtil.setCurrentCommand(commandType)
        task.execute(medtronicCommunicationManager)

        if (!task.isReceived) {
            aapsLogger.warn(LTag.PUMP, "Reply not received for $commandType")
        }

        task.postProcess(medtronicUIPostprocessor)
        return task
    }

    val invalidResponsesCount: Int
        get() = medtronicCommunicationManager.notConnectedCount

}