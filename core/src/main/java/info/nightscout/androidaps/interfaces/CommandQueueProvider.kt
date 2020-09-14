package info.nightscout.androidaps.interfaces

import android.text.Spanned
import info.nightscout.androidaps.data.DetailedBolusInfo
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.queue.commands.CustomCommand
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.queue.commands.Command

interface CommandQueueProvider {

    fun isRunning(type: Command.CommandType): Boolean
    fun pickup()
    fun clear()
    fun size(): Int
    fun performing(): Command?
    fun resetPerforming()
    fun independentConnect(reason: String, callback: Callback?)
    fun bolusInQueue(): Boolean
    fun bolus(detailedBolusInfo: DetailedBolusInfo, callback: Callback?): Boolean
    fun cancelAllBoluses()
    fun stopPump(callback: Callback?)
    fun startPump(callback: Callback?)
    fun setTBROverNotification(callback: Callback?, enable: Boolean)
    fun tempBasalAbsolute(absoluteRate: Double, durationInMinutes: Int, enforceNew: Boolean, profile: Profile, callback: Callback?): Boolean
    fun tempBasalPercent(percent: Int, durationInMinutes: Int, enforceNew: Boolean, profile: Profile, callback: Callback?): Boolean
    fun extendedBolus(insulin: Double, durationInMinutes: Int, callback: Callback?): Boolean
    fun cancelTempBasal(enforceNew: Boolean, callback: Callback?): Boolean
    fun cancelExtended(callback: Callback?): Boolean
    fun setProfile(profile: Profile, callback: Callback?): Boolean
    fun readStatus(reason: String, callback: Callback?): Boolean
    fun statusInQueue(): Boolean
    fun loadHistory(type: Byte, callback: Callback?): Boolean
    fun setUserOptions(callback: Callback?): Boolean
    fun loadTDDs(callback: Callback?): Boolean
    fun loadEvents(callback: Callback?): Boolean
    fun customCommand(customCommand: CustomCommand, callback: Callback?): Boolean
    fun isCustomCommandRunning(customCommandType: Class<out CustomCommand>): Boolean
    fun isCustomCommandInQueue(customCommandType: Class<out CustomCommand>): Boolean
    fun spannedStatus(): Spanned
    fun isThisProfileSet(profile: Profile): Boolean
}