package info.nightscout.androidaps.dependencyInjection

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.queue.CommandQueue
import info.nightscout.androidaps.queue.commands.*

@Module
@Suppress("unused")
abstract class CommandQueueModule {

    @ContributesAndroidInjector abstract fun commandQueueInjector(): CommandQueue
    @ContributesAndroidInjector abstract fun commandBolusInjector(): CommandBolus
    @ContributesAndroidInjector abstract fun commandCancelExtendedBolusInjector(): CommandCancelExtendedBolus
    @ContributesAndroidInjector abstract fun commandCancelTempBasalInjector(): CommandCancelTempBasal
    @ContributesAndroidInjector abstract fun commandExtendedBolusInjector(): CommandExtendedBolus
    @ContributesAndroidInjector abstract fun commandInsightSetTBROverNotificationInjector(): CommandInsightSetTBROverNotification
    @ContributesAndroidInjector abstract fun commandLoadEventsInjector(): CommandLoadEvents
    @ContributesAndroidInjector abstract fun commandLoadHistoryInjector(): CommandLoadHistory
    @ContributesAndroidInjector abstract fun commandLoadTDDsInjector(): CommandLoadTDDs
    @ContributesAndroidInjector abstract fun commandReadStatusInjector(): CommandReadStatus
    @ContributesAndroidInjector abstract fun commandSetProfileInjector(): CommandSetProfile
    @ContributesAndroidInjector abstract fun commandCommandSMBBolusInjector(): CommandSMBBolus
    @ContributesAndroidInjector abstract fun commandStartPumpInjector(): CommandStartPump
    @ContributesAndroidInjector abstract fun commandStopPumpInjector(): CommandStopPump
    @ContributesAndroidInjector abstract fun commandTempBasalAbsoluteInjector(): CommandTempBasalAbsolute
    @ContributesAndroidInjector abstract fun commandTempBasalPercentInjector(): CommandTempBasalPercent
    @ContributesAndroidInjector abstract fun commandSetUserSettingsInjector(): CommandSetUserSettings
    @ContributesAndroidInjector abstract fun commandCustomCommandInjector(): CommandCustomCommand
}