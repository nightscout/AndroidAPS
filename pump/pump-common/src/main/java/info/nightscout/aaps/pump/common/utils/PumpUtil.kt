package info.nightscout.aaps.pump.common.utils

import android.content.Context
import com.google.gson.GsonBuilder
import info.nightscout.aaps.pump.common.data.DateTimeDto
import info.nightscout.aaps.pump.common.defs.PumpErrorType
import info.nightscout.aaps.pump.common.defs.NotificationTypeInterface
import info.nightscout.aaps.pump.common.driver.connector.defs.PumpCommandType
import info.nightscout.aaps.pump.common.events.EventPumpDriverStateChanged
import info.nightscout.core.events.EventNewNotification
import info.nightscout.interfaces.notifications.Notification
import info.nightscout.aaps.pump.common.defs.PumpDriverState
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.rx.logging.LTag

open class PumpUtil constructor(
    val aapsLogger: AAPSLogger,
    val rxBus: RxBus,
    val context: Context,
    val resourceHelper: ResourceHelper
) {

    var preventConnect: Boolean = false

    //private var driverStatusInternal: PumpDriverState
    private var pumpCommandType: PumpCommandType? = null
    var gson = GsonBuilder().setPrettyPrinting().create()
    var gsonRegular = GsonBuilder().create()


    fun resetDriverStatusToConnected() {
        workWithStatusAndCommand(StatusChange.SetStatus, PumpDriverState.Ready, null)
    }

    var driverStatus: PumpDriverState
        get() {
            var stat = workWithStatusAndCommand(StatusChange.GetStatus, null, null) as PumpDriverState
            aapsLogger.debug(LTag.PUMP, "Get driver status: " + stat.name)
            return stat
        }
        set(status) {
            aapsLogger.debug(LTag.PUMP, "Set driver status: " + status.name)
            workWithStatusAndCommand(StatusChange.SetStatus, status, null)
        }

    var currentCommand: PumpCommandType?
        get() {
            val returnValue = workWithStatusAndCommand(StatusChange.GetCommand, null, null)
            if (returnValue == null)
                return null
            else
                return returnValue as PumpCommandType
        }
        set(currentCommand) {
            if (currentCommand == null) {
                aapsLogger.debug(LTag.PUMP, "Set current command: to null")
            } else {
                aapsLogger.debug(LTag.PUMP, "Set current command: " + currentCommand.name)
            }
            workWithStatusAndCommand(StatusChange.SetCommand, PumpDriverState.ExecutingCommand, currentCommand)
        }

    var errorType: PumpErrorType?
        get() {
            return workWithStatusAndCommand(StatusChange.GetError, null, null) as PumpErrorType?
        }
        set(error) {
            workWithStatusAndCommand(StatusChange.SetError, null, null, error)
        }


    @Synchronized
    fun workWithStatusAndCommand(
        type: StatusChange,
        driverStatusIn: PumpDriverState?,
        pumpCommandType: PumpCommandType?,
        pumpErrorType: PumpErrorType? = null
    ): Any? {

        //aapsLogger.debug(LTag.PUMP, "Status change type: " + type.name() + ", DriverStatus: " + (driverStatus != null ? driverStatus.name() : ""));
        when (type) {
            StatusChange.GetStatus  -> {
                //aapsLogger.debug(LTag.PUMP, "GetStatus: DriverStatus: " + driverStatusInternal);
                return driverStatusInternal
            }

            StatusChange.SetStatus  -> {
                //aapsLogger.debug(LTag.PUMP, "SetStatus: DriverStatus Before: " + driverStatusInternal + ", Incoming: " + driverStatusIn);
                driverStatusInternal = driverStatusIn!!
                this.pumpCommandType = null
                //aapsLogger.debug(LTag.PUMP, "SetStatus: DriverStatus: " + driverStatusInternal);
                rxBus.send(EventPumpDriverStateChanged(driverStatusInternal))
            }

            StatusChange.GetCommand -> return this.pumpCommandType

            StatusChange.SetCommand -> {
                driverStatusInternal = driverStatusIn!!
                this.pumpCommandType = pumpCommandType
                rxBus.send(EventPumpDriverStateChanged(driverStatusInternal))
            }

            StatusChange.GetError   -> return errorTypeInternal

            StatusChange.SetError   -> {
                errorTypeInternal = pumpErrorType!!
                this.pumpCommandType = null
                driverStatusInternal = PumpDriverState.ErrorCommunicatingWithPump
                rxBus.send(EventPumpDriverStateChanged(driverStatusInternal))
            }
        }
        return null
    }

    fun sleepSeconds(seconds: Long) {
        try {
            Thread.sleep(seconds * 1000)
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    fun sleep(miliseconds: Long) {
        try {
            Thread.sleep(miliseconds)
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    fun toDateTimeDto(atechDateTimeIn: Long): DateTimeDto {
        var atechDateTime = atechDateTimeIn
        val year = (atechDateTime / 10000000000L).toInt()
        atechDateTime -= year * 10000000000L
        val month = (atechDateTime / 100000000L).toInt()
        atechDateTime -= month * 100000000L
        val dayOfMonth = (atechDateTime / 1000000L).toInt()
        atechDateTime -= dayOfMonth * 1000000L
        val hourOfDay = (atechDateTime / 10000L).toInt()
        atechDateTime -= hourOfDay * 10000L
        val minute = (atechDateTime / 100L).toInt()
        atechDateTime -= minute * 100L
        val second = atechDateTime.toInt()
        return DateTimeDto(year, month, dayOfMonth, hourOfDay, minute, second)
    }

    fun fromDpToSize(dpSize: Int): Int {
        val scale = context.resources.displayMetrics.density
        val pixelsFl = ((dpSize * scale) + 0.5f)
        return pixelsFl.toInt()
    }

    enum class StatusChange {
        GetStatus, GetCommand, SetStatus, SetCommand, GetError, SetError
    }



    fun isSame(d1: Double, d2: Double): Boolean {
        val diff = d1 - d2
        return Math.abs(diff) <= 0.000001
    }

    fun isSame(d1: Double, d2: Int): Boolean {
        val diff = d1 - d2
        return Math.abs(diff) <= 0.000001
    }

    fun sendNotification(notificationType: NotificationTypeInterface) {
        val notification = Notification( //
            notificationType.notificationType,  //
            resourceHelper.gs(notificationType.resourceId),  //
            notificationType.notificationUrgency
        )
        rxBus.send(EventNewNotification(notification))
    }

    fun sendNotification(notificationType: NotificationTypeInterface, vararg parameters: Any?) {
        val notification = Notification( //
            notificationType.notificationType,  //
            resourceHelper.gs(notificationType.resourceId, *parameters),  //
            notificationType.notificationUrgency
        )
        rxBus.send(EventNewNotification(notification))
    }



    companion object {

        const val MAX_RETRY = 2
        //private var driverStatus = PumpDriverState.Sleeping

        @JvmStatic private var driverStatusInternal: PumpDriverState = PumpDriverState.Sleeping
        @JvmStatic private var errorTypeInternal: PumpErrorType? = null

    }
}
