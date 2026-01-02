package app.aaps.pump.medtronic.comm.ui

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.medtronic.comm.MedtronicCommunicationManager
import app.aaps.pump.medtronic.defs.MedtronicCommandType
import app.aaps.pump.medtronic.util.MedtronicUtil
import javax.inject.Inject
import javax.inject.Provider

/**
 * Created by andy on 6/14/18.
 */
class MedtronicUIComm @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val medtronicUtil: MedtronicUtil,
    private val medtronicUIPostprocessor: MedtronicUIPostprocessor,
    private val medtronicCommunicationManager: MedtronicCommunicationManager,
    private val medtronicUITaskProvider: Provider<MedtronicUITask>
) {

    fun executeCommand(commandType: MedtronicCommandType): MedtronicUITask {
        return executeCommand(commandType, null)
    }

    @Synchronized
    fun executeCommand(commandType: MedtronicCommandType, parameters: ArrayList<Any>?): MedtronicUITask {

        aapsLogger.info(LTag.PUMP, "Execute Command: " + commandType.name)
        val task = medtronicUITaskProvider.get().with(commandType, parameters)
        medtronicUtil.setCurrentCommand(commandType)
        task.execute(medtronicCommunicationManager)

        if (!task.isReceived) {
            aapsLogger.warn(LTag.PUMP, "Reply not received for $commandType")
        }

        task.postProcess(medtronicUIPostprocessor)
        return task
    }

    val invalidResponsesCount: Int
        get() = medtronicCommunicationManager.getNotConnectedCount()

}