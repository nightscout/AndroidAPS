package info.nightscout.aaps.pump.common.driver.connector.mgr

import android.content.Context
import dagger.android.HasAndroidInjector

import info.nightscout.aaps.pump.common.data.BasalProfileDto
import info.nightscout.aaps.pump.common.driver.connector.PumpConnectorInterface

import info.nightscout.aaps.pump.common.defs.PumpConfigurationTypeInterface
import info.nightscout.aaps.pump.common.driver.connector.commands.data.AdditionalResponseDataInterface
import info.nightscout.aaps.pump.common.driver.connector.commands.data.CustomCommandTypeInterface
import info.nightscout.aaps.pump.common.driver.connector.commands.response.DataCommandResponse
import info.nightscout.aaps.pump.common.driver.connector.defs.PumpCommandType
import info.nightscout.interfaces.profile.Profile
import info.nightscout.interfaces.pump.DetailedBolusInfo
import info.nightscout.pump.common.data.PumpStatus
import info.nightscout.aaps.pump.common.data.PumpTimeDifferenceDto
import info.nightscout.pump.common.defs.PumpDriverState
import info.nightscout.pump.common.defs.TempBasalPair
import info.nightscout.pump.common.utils.PumpUtil
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.sharedPreferences.SP
import io.reactivex.rxjava3.disposables.CompositeDisposable

abstract class PumpConnectionManager constructor(
    val pumpStatus: PumpStatus,
    val pumpUtil: PumpUtil,
    val sp: SP,
    val injector: HasAndroidInjector,
    val aapsLogger: AAPSLogger,
    val rxBus: RxBus,
    //val fabricPrivacy: FabricPrivacy,
    val context: Context,
    //val tandemDataConverter: TandemDataConverter,
    //val tandemPumpConnector: TandemPumpConnector
) {

    //private val fabricPrivacy: FabricPrivacy
    // private val baseConnector // YpsoPumpBaseConnector
    //     : PumpConnectorInterface
    // private val selectedConnector: PumpConnectorInterface
    private val disposable = CompositeDisposable()
    //private var oldFirmware: TandemPumpApiVersion? = null
    //private var currentFirmware: TandemPumpApiVersion? = null
    var inConnectMode = false
    var inDisconnectMode = false
    
    val TAG = LTag.PUMPCOMM

    //var lateinit tandemCommunicationManager: TandemCommunicationManager

    // var deviceMac: String? = null
    // var deviceBonded: Boolean = false

    abstract fun connectToPump(): Boolean

    abstract fun disconnectFromPump(): Boolean

    abstract fun setCurrentPumpCommandType(commandType: PumpCommandType)

    abstract fun resetDriverStatus()

    abstract fun getConnector(commandType: PumpCommandType?): PumpConnectorInterface

    abstract fun postProcessConfiguration(value: MutableMap<PumpConfigurationTypeInterface, Any>?)


    fun connectToPumpX(): Boolean {

        // TODO handle states
        aapsLogger.debug(TAG, "!!!!!! Connect to Pump")
        pumpUtil.driverStatus = PumpDriverState.Connecting
        // pumpUtil.sleepSeconds(15)

        val connected = getConnector(null).connectToPump()

        if (connected) {
            pumpUtil.driverStatus = PumpDriverState.Connected
            pumpUtil.driverStatus = PumpDriverState.Ready
        } else {
            pumpUtil.driverStatus = PumpDriverState.ErrorCommunicatingWithPump
        }

        // pumpUtil.driverStatus = PumpDriverState.Connected
        // pumpUtil.sleepSeconds(5)
        // pumpUtil.driverStatus = PumpDriverState.EncryptCommunication
        // pumpUtil.sleepSeconds(5)
        // pumpUtil.driverStatus = PumpDriverState.Ready


        // Thread thread = new Thread() {
        //     public void run() {
        //         println("Thread Running")
        //         aapsLogger.debug(TAG, "!!!!!! Connect to Pump - Thread running")
        //         ypsopumpUtil.driverStatus = PumpDriverState.Connecting
        //         ypsopumpUtil.sleepSeconds(15)
        //         ypsopumpUtil.driverStatus = PumpDriverState.Connected
        //         ypsopumpUtil.sleepSeconds(5)
        //         ypsopumpUtil.driverStatus = PumpDriverState.EncryptCommunication
        //         ypsopumpUtil.sleepSeconds(5)
        //         ypsopumpUtil.driverStatus = PumpDriverState.Ready
        //
        //     }
        // };
        //
        // thread.start();

        return connected
    }


//     fun connectToPump(deviceMac: String, deviceBonded: Boolean): Boolean {
//
//         if (pumpUtil.driverStatus === PumpDriverState.Ready) {
//             return true
//         }
//
//         if (inConnectMode)
//             return false;
//
//         if (deviceMac.isNullOrEmpty() && !deviceBonded) {
//             return false
//         }
//
//         inConnectMode = true
//
//         // TODO
//         //val deviceMac = "EC:2A:F0:00:8B:8E"
//
//         //sp.getString()
//
//         if (!ypsoPumpBLE.startConnectToYpsoPump(deviceMac)) {
//             inConnectMode = false
//             return false
//         }
//
//         val timeoutTime = System.currentTimeMillis() + (120 * 1000)
//         var timeouted = true
//         var driverStatus: PumpDriverState?
//
//         while (System.currentTimeMillis() < timeoutTime) {
//             SystemClock.sleep(5000)
//
//             driverStatus = pumpUtil.driverStatus
//
//             aapsLogger.debug(TAGCOMM, "connectToPump: " + driverStatus.name)
//
//             if (driverStatus == PumpDriverState.Ready || driverStatus == PumpDriverState.ErrorCommunicatingWithPump) {
//                 timeouted = false
//                 break
//             }
//         }
//
//         inConnectMode = false
//         return true
//
// //         // TODO if initialized use types connection, else use base one
// //
// // //        Thread thread = new Thread() {
// // //            public void run() {
// //         println("Thread Running")
// //         aapsLogger.debug(TAG, "!!!!!! Connect to Pump - Thread running")
// //         ypsopumpUtil.driverStatus = PumpDriverState.Connecting
// //         ypsopumpUtil.sleepSeconds(15)
// //         ypsopumpUtil.driverStatus = PumpDriverState.Connected
// //         ypsopumpUtil.sleepSeconds(5)
// //         ypsopumpUtil.driverStatus = PumpDriverState.EncryptCommunication
// //         ypsopumpUtil.sleepSeconds(5)
// //         ypsopumpUtil.driverStatus = PumpDriverState.Ready
//
// //            }
// //        };
// //
// //        thread.start();
//
//     }


    open fun resetFirmwareVersion() {
    }

    open fun determineFirmwareVersion() {
    }

//     fun executeCommand(parameters: CommandParameters): ResultCommandResponse {
//
// //        ypsopumpUtil.setDriverStatus(YpsoDriverStatus.Connecting);
// //        ypsopumpUtil.sleepSeconds(5);
// //
// //        if (connectToPump()) {
// //
// //            ypsopumpUtil.setDriverStatus(YpsoDriverStatus.Connected);
// //
// //        } else {
// //
// //            return CommandResponse.builder().success(false).build();
// //
// //        }
//
//         // TODO single only ATM
//         pumpUtil.currentCommand = parameters.commandType
//         pumpUtil.sleepSeconds(40)
//         pumpUtil.driverStatus = PumpDriverState.Connected
//         return true
//     }


    fun disconnectFromPumpX(): Boolean {

        // TODO handle states
        return getConnector(null).disconnectFromPump()



        // var driverStatus: PumpDriverState? = pumpUtil.driverStatus
        //
        // when (driverStatus) {
        //     PumpDriverState.NotInitialized,
        //     PumpDriverState.Initialized                -> {
        //         aapsLogger.warn(TAGCOMM, "disconnectFromPump. Pump is in weird state ($driverStatus.name), exiting.")
        //         return true;
        //     }
        //
        //     PumpDriverState.ErrorCommunicatingWithPump -> {
        //         val errorType = pumpUtil.errorType
        //         aapsLogger.warn(TAGCOMM, "disconnectFromPump. Pump is in error ($errorType.name), exiting.")
        //         return true;
        //     }
        //
        //     PumpDriverState.Connecting,
        //     PumpDriverState.EncryptCommunication,
        //     PumpDriverState.ExecutingCommand,
        //     PumpDriverState.Busy,
        //     PumpDriverState.Suspended,
        //     PumpDriverState.Connected                  -> {
        //         aapsLogger.warn(TAGCOMM, "disconnectFromPump. Pump seems to be in unallowed state ($driverStatus.name), exiting and setting to Sleep.")
        //         pumpUtil.driverStatus = PumpDriverState.Sleeping
        //         return;
        //     }
        //
        //     PumpDriverState.Disconnecting              -> {
        //         aapsLogger.warn(TAGCOMM, "disconnectFromPump. Pump is already disconnecting, exiting.")
        //         return true
        //     }
        //
        //     PumpDriverState.Sleeping,
        //     PumpDriverState.Disconnected,
        //     PumpDriverState.Ready                        -> return true
        //
        //     null -> {}
        // }
        //
        // if (inDisconnectMode)
        //     return true
        //
        // inDisconnectMode = true
        //
        // ypsoPumpBLE.disconnectFromYpsoPump()
        //
        // val timeoutTime = System.currentTimeMillis() + (120 * 1000)
        // var timeouted = true
        // //var driverStatus: PumpDriverState? = null
        //
        // while (System.currentTimeMillis() < timeoutTime) {
        //     SystemClock.sleep(5000)
        //
        //     driverStatus = pumpUtil.driverStatus
        //
        //     aapsLogger.debug(TAGCOMM, "disconnectFromPump: " + driverStatus.name)
        //
        //     if (driverStatus == PumpDriverState.Disconnected || driverStatus == PumpDriverState.Sleeping) {
        //         timeouted = false
        //         break
        //     }
        // }
        //
        // if (driverStatus == PumpDriverState.Disconnected) {
        //     pumpUtil.driverStatus = PumpDriverState.Sleeping
        // }
        //
        // inDisconnectMode = false

        // //Thread thread = new Thread() {
        // //  public void run() {
        // println("Thread Running")
        // aapsLogger.debug(TAG, "Disconnect from Pump - Thread running")
        // ypsopumpUtil.driverStatus = PumpDriverState.Disconnecting
        // ypsopumpUtil.sleepSeconds(20)
        // ypsopumpUtil.driverStatus = PumpDriverState.Sleeping
        // pumpStatus.setLastCommunicationToNow()
        // }
        //};
    }



    fun deliverBolus(detailedBolusInfo: DetailedBolusInfo?): DataCommandResponse<AdditionalResponseDataInterface?> {

        val responseData: DataCommandResponse<AdditionalResponseDataInterface?> = getConnectorData(PumpCommandType.SetBolus)
        {
            getConnector(PumpCommandType.SetBolus).sendBolus(detailedBolusInfo!!)
        }

        checkAdditionalResponseData(PumpCommandType.SetBolus, responseData)

        return responseData
    }


    fun getTemporaryBasal(): DataCommandResponse<TempBasalPair?> {

        val responseData: DataCommandResponse<TempBasalPair?> = getConnectorData(PumpCommandType.GetTemporaryBasal)
        {
            getConnector(PumpCommandType.GetTemporaryBasal).retrieveTemporaryBasal()
        }

        return responseData
    }


    fun setTemporaryBasal(value: Int, duration: Int): DataCommandResponse<AdditionalResponseDataInterface?> {

        val responseData: DataCommandResponse<AdditionalResponseDataInterface?> = getConnectorData(PumpCommandType.SetTemporaryBasal)
        {
            getConnector(PumpCommandType.SetTemporaryBasal).sendTemporaryBasal(value, duration)
        }

        checkAdditionalResponseData(PumpCommandType.SetTemporaryBasal, responseData)

        return responseData
    }


    fun setBasalProfile(profile: Profile?): DataCommandResponse<AdditionalResponseDataInterface?> {

        val responseData: DataCommandResponse<AdditionalResponseDataInterface?> = getConnectorData(PumpCommandType.SetBasalProfile)
        {
            getConnector(PumpCommandType.SetBasalProfile).sendBasalProfile(profile!!) // TODO refactor this not to use AAPS object
        }

        checkAdditionalResponseData(PumpCommandType.SetBasalProfile, responseData)

        return responseData
    }


    fun cancelTemporaryBasal(): DataCommandResponse<AdditionalResponseDataInterface?> {
        val responseData: DataCommandResponse<AdditionalResponseDataInterface?> = getConnectorData(PumpCommandType.CancelTemporaryBasal)
        {
            getConnector(PumpCommandType.CancelTemporaryBasal).cancelTemporaryBasal() // TODO refactor this not to use AAPS object
        }

        checkAdditionalResponseData(PumpCommandType.CancelTemporaryBasal, responseData)

        return responseData
    }

    fun getRemainingInsulin(): DataCommandResponse<Double?> {

        val responseData: DataCommandResponse<Double?> = getConnectorData(PumpCommandType.GetRemainingInsulin)
        {
            getConnector(PumpCommandType.GetRemainingInsulin).retrieveRemainingInsulin()
        }

        if (responseData.isSuccess()) {
            pumpStatus.reservoirRemainingUnits = responseData.value!!
        }

        return responseData

    }


    fun getConfiguration(): DataCommandResponse<MutableMap<PumpConfigurationTypeInterface, Any>?> {

        val responseData: DataCommandResponse<MutableMap<PumpConfigurationTypeInterface, Any>?> = getConnectorData(PumpCommandType.GetSettings)
        {
            getConnector(PumpCommandType.GetSettings).retrieveConfiguration()
        }

        if (responseData.isSuccess()) {
            postProcessConfiguration(responseData.value);
        }

        return responseData

    }


    fun getBasalProfile(): DataCommandResponse<BasalProfileDto?> {

        val responseData: DataCommandResponse<BasalProfileDto?> = getConnectorData(PumpCommandType.GetBasalProfile)
        {
            getConnector(PumpCommandType.GetBasalProfile).retrieveBasalProfile()
        }

        if (responseData.isSuccess()) {
            postProcessBasalProfile(responseData.value!!);
        }

        return responseData

    }


    fun getTime(): DataCommandResponse<PumpTimeDifferenceDto?> {

        val responseData: DataCommandResponse<PumpTimeDifferenceDto?> = getConnectorData(PumpCommandType.GetTime)
        {
            getConnector(PumpCommandType.GetTime).getTime()
        }

        if (responseData.isSuccess()) {
            postProcessTime(responseData.value)
        }

        return responseData
    }


    fun setTime(): DataCommandResponse<AdditionalResponseDataInterface?> {

        val responseData: DataCommandResponse<AdditionalResponseDataInterface?> = getConnectorData(PumpCommandType.SetTime)
        {
            getConnector(PumpCommandType.SetTime).setTime() // TODO refactor this not to use AAPS object
        }

        checkAdditionalResponseData(PumpCommandType.SetTime, responseData)

        return responseData

    }


    fun getBatteryLevel(): DataCommandResponse<Int?> {

        val responseData: DataCommandResponse<Int?> = getConnectorData(PumpCommandType.GetBatteryStatus)
        {
            getConnector(PumpCommandType.GetBatteryStatus).retrieveBatteryStatus()
        }

        if (responseData.isSuccess()) {
            pumpStatus.batteryRemaining = responseData.value!!
        }

        return responseData
    }


    fun executeCustomCommand(command: CustomCommandTypeInterface): DataCommandResponse<AdditionalResponseDataInterface?> {

        val responseData: DataCommandResponse<AdditionalResponseDataInterface?> = getConnectorData(PumpCommandType.CustomCommand)
        {
            getConnector(PumpCommandType.CustomCommand).executeCustomCommand(command)
        }

        if (responseData.isSuccess()) {
            postProcessCustomCommand(responseData)
        }

        return responseData
    }


    open fun postProcessTime(value: PumpTimeDifferenceDto?) {
        pumpStatus.pumpTime  = value
    }


    open fun postProcessBasalProfile(value: BasalProfileDto) {
        pumpStatus.basalsByHour = value.basalPatterns
        if (value.basalName!=null) {
            pumpStatus.activeProfileName = value.basalName!!
        }
    }


    open fun postProcessCustomCommand(responseData: DataCommandResponse<AdditionalResponseDataInterface?>) {

    }


    private inline fun <reified T>  getConnectorData(commandType: PumpCommandType,
                                                     customCommandType: CustomCommandTypeInterface? = null,
                                                     getData: () -> DataCommandResponse<T>
    ): DataCommandResponse<T> {

        val commandTypeDescription = if (customCommandType==null) {
            commandType.name
        } else {
            commandType.name + " ${customCommandType.getKey()}"
        }

        aapsLogger.info(TAG, "TANDEMDBG: Executing Command: ${commandTypeDescription}  - START ")

        // TODO check if command is available, if not return error

        setCurrentPumpCommandType(commandType)
        val response = getData()
        resetDriverStatus()

        if (response.isSuccess) {
            aapsLogger.info(TAG, "TANDEMDBG: Command was SUCCESSFUL - Data: ${response.value}")
        } else {
            aapsLogger.error(TAG, "TANDEMDBG: Command FAILED with description ${response.errorDescription}")
        }

        aapsLogger.info(TAG, "TANDEMDBG: Executing Command: ${commandTypeDescription}  - END ")

        return response
    }


    private fun checkAdditionalResponseData(commandType: PumpCommandType, responseData: DataCommandResponse<AdditionalResponseDataInterface?>) {
        if (responseData.value!=null) {
            processAdditionalResponseData(commandType, responseData)
        }
    }


    abstract fun processAdditionalResponseData(commandType: PumpCommandType, responseData: DataCommandResponse<AdditionalResponseDataInterface?>)


    init {
        // TODO this can be changed, since we have only one connector

        // TODO change this
        //baseConnector = PumpDummyConnector(pumpStatus, pumpUtil, injector, aapsLogger)

        //baseConnector = tandemPumpConnector

        //selectedConnector = baseConnector //new YpsoPumpDummyConnector(ypsopumpUtil, injector, aapsLogger);
        //this.fabricPrivacy = fabricPrivacy
        // disposable.add(rxBus
        //                    .toObservable(EventPumpConfigurationChanged::class.java)
        //                    .observeOn(Schedulers.io())
        //                    .subscribe({ _ -> resetFirmwareVersion() }) { throwable: Throwable? -> fabricPrivacy.logException(throwable!!) }
        // )
    }
}