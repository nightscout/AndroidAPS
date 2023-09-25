package app.aaps.implementation.di

import app.aaps.implementation.queue.CommandQueueImplementation
import app.aaps.implementation.queue.commands.CommandBolus
import app.aaps.implementation.queue.commands.CommandCancelExtendedBolus
import app.aaps.implementation.queue.commands.CommandCancelTempBasal
import app.aaps.implementation.queue.commands.CommandClearAlarms
import app.aaps.implementation.queue.commands.CommandCustomCommand
import app.aaps.implementation.queue.commands.CommandDeactivate
import app.aaps.implementation.queue.commands.CommandExtendedBolus
import app.aaps.implementation.queue.commands.CommandInsightSetTBROverNotification
import app.aaps.implementation.queue.commands.CommandLoadEvents
import app.aaps.implementation.queue.commands.CommandLoadHistory
import app.aaps.implementation.queue.commands.CommandLoadTDDs
import app.aaps.implementation.queue.commands.CommandReadStatus
import app.aaps.implementation.queue.commands.CommandSMBBolus
import app.aaps.implementation.queue.commands.CommandSetProfile
import app.aaps.implementation.queue.commands.CommandSetUserSettings
import app.aaps.implementation.queue.commands.CommandStartPump
import app.aaps.implementation.queue.commands.CommandStopPump
import app.aaps.implementation.queue.commands.CommandTempBasalAbsolute
import app.aaps.implementation.queue.commands.CommandTempBasalPercent
import app.aaps.implementation.queue.commands.CommandUpdateTime
import dagger.Module
import dagger.android.ContributesAndroidInjector

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