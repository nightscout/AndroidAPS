package info.nightscout.implementation.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.implementation.queue.CommandQueueImplementation
import info.nightscout.implementation.queue.commands.CommandReadStatus
import info.nightscout.implementation.queue.commands.CommandSMBBolus
import info.nightscout.implementation.queue.commands.CommandSetProfile
import info.nightscout.implementation.queue.commands.CommandSetUserSettings
import info.nightscout.implementation.queue.commands.CommandStartPump
import info.nightscout.implementation.queue.commands.CommandStopPump
import info.nightscout.implementation.queue.commands.CommandTempBasalAbsolute
import info.nightscout.implementation.queue.commands.CommandTempBasalPercent
import info.nightscout.implementation.queue.commands.CommandBolus
import info.nightscout.implementation.queue.commands.CommandCancelExtendedBolus
import info.nightscout.implementation.queue.commands.CommandCancelTempBasal
import info.nightscout.implementation.queue.commands.CommandClearAlarms
import info.nightscout.implementation.queue.commands.CommandCustomCommand
import info.nightscout.implementation.queue.commands.CommandDeactivate
import info.nightscout.implementation.queue.commands.CommandExtendedBolus
import info.nightscout.implementation.queue.commands.CommandInsightSetTBROverNotification
import info.nightscout.implementation.queue.commands.CommandLoadEvents
import info.nightscout.implementation.queue.commands.CommandLoadHistory
import info.nightscout.implementation.queue.commands.CommandLoadTDDs
import info.nightscout.implementation.queue.commands.CommandUpdateTime

@Module
@Suppress("unused")
abstract class CommandQueueModule {

    @ContributesAndroidInjector abstract fun commandQueueInjector(): CommandQueueImplementation
    @ContributesAndroidInjector abstract fun commandBolusInjector(): CommandBolus
    @ContributesAndroidInjector abstract fun commandCancelExtendedBolusInjector(): CommandCancelExtendedBolus
    @ContributesAndroidInjector abstract fun commandCancelTempBasalInjector(): CommandCancelTempBasal
    @ContributesAndroidInjector abstract fun commandExtendedBolusInjector(): CommandExtendedBolus
    @ContributesAndroidInjector abstract fun commandInsightSetTBROverNotificationInjector(): CommandInsightSetTBROverNotification
    @ContributesAndroidInjector abstract fun commandLoadEventsInjector(): CommandLoadEvents
    @ContributesAndroidInjector abstract fun commandClearAlarmsInjector(): CommandClearAlarms
    @ContributesAndroidInjector abstract fun commandDeactivateInjector(): CommandDeactivate
    @ContributesAndroidInjector abstract fun commandUpdateTimeInjector(): CommandUpdateTime
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