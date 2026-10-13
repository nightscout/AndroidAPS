package app.aaps.core.interfaces.queue

import android.text.Spanned
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.PumpSync

interface CommandQueue {

    var waitingForDisconnect: Boolean

    fun isRunning(type: Command.CommandType): Boolean
    fun pickup()
    fun clear()
    fun size(): Int
    fun performing(): Command?
    fun resetPerforming()
    fun bolusInQueue(): Boolean
    fun bolus(detailedBolusInfo: DetailedBolusInfo, callback: Callback?): Boolean
    fun cancelAllBoluses(id: Long?)
    fun stopPump(callback: Callback?)
    fun startPump(callback: Callback?)
    fun setTBROverNotification(callback: Callback?, enable: Boolean)
    fun tempBasalAbsolute(absoluteRate: Double, durationInMinutes: Int, enforceNew: Boolean, profile: Profile, tbrType: PumpSync.TemporaryBasalType, callback: Callback?): Boolean
    fun tempBasalPercent(percent: Int, durationInMinutes: Int, enforceNew: Boolean, profile: Profile, tbrType: PumpSync.TemporaryBasalType, callback: Callback?): Boolean
    fun extendedBolus(insulin: Double, durationInMinutes: Int, callback: Callback?): Boolean
    fun cancelTempBasal(enforceNew: Boolean, autoForced: Boolean = false, callback: Callback?): Boolean
    fun cancelExtended(callback: Callback?): Boolean
    fun readStatus(reason: String, callback: Callback?): Boolean
    fun statusInQueue(): Boolean
    fun loadHistory(type: Byte, callback: Callback?): Boolean
    fun setUserOptions(callback: Callback?): Boolean
    fun loadTDDs(callback: Callback?): Boolean
    fun loadEvents(callback: Callback?): Boolean
    fun clearAlarms(callback: Callback?): Boolean
    fun deactivate(callback: Callback?): Boolean
    fun updateTime(callback: Callback?): Boolean
    fun customCommand(customCommand: CustomCommand, callback: Callback?): Boolean
    fun isCustomCommandRunning(customCommandType: Class<out CustomCommand>): Boolean
    fun isCustomCommandInQueue(customCommandType: Class<out CustomCommand>): Boolean
    fun spannedStatus(): Spanned
    fun isThisProfileSet(requestedProfile: Profile): Boolean
}