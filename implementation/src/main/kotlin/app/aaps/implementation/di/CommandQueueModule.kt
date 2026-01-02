package app.aaps.implementation.di

import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.implementation.queue.CommandQueueImplementation
import app.aaps.implementation.queue.CommandQueueName
import app.aaps.implementation.queue.QueueWorker
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
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.android.ContributesAndroidInjector

@Module(
    includes = [
        CommandQueueModule.Bindings::class
    ]
)

open class CommandQueueModule {

    @Suppress("unused")
    @Module
    interface Bindings {

        @Binds fun bindCommandQueueInjector(commandQueueImplementation: CommandQueueImplementation): CommandQueue

        @ContributesAndroidInjector fun queueWorkerInjector(): QueueWorker
        @ContributesAndroidInjector fun commandBolusInjector(): CommandBolus
        @ContributesAndroidInjector fun commandCancelExtendedBolusInjector(): CommandCancelExtendedBolus
        @ContributesAndroidInjector fun commandCancelTempBasalInjector(): CommandCancelTempBasal
        @ContributesAndroidInjector fun commandExtendedBolusInjector(): CommandExtendedBolus
        @ContributesAndroidInjector fun commandInsightSetTBROverNotificationInjector(): CommandInsightSetTBROverNotification
        @ContributesAndroidInjector fun commandLoadEventsInjector(): CommandLoadEvents
        @ContributesAndroidInjector fun commandClearAlarmsInjector(): CommandClearAlarms
        @ContributesAndroidInjector fun commandDeactivateInjector(): CommandDeactivate
        @ContributesAndroidInjector fun commandUpdateTimeInjector(): CommandUpdateTime
        @ContributesAndroidInjector fun commandLoadHistoryInjector(): CommandLoadHistory
        @ContributesAndroidInjector fun commandLoadTDDsInjector(): CommandLoadTDDs
        @ContributesAndroidInjector fun commandReadStatusInjector(): CommandReadStatus
        @ContributesAndroidInjector fun commandSetProfileInjector(): CommandSetProfile
        @ContributesAndroidInjector fun commandCommandSMBBolusInjector(): CommandSMBBolus
        @ContributesAndroidInjector fun commandStartPumpInjector(): CommandStartPump
        @ContributesAndroidInjector fun commandStopPumpInjector(): CommandStopPump
        @ContributesAndroidInjector fun commandTempBasalAbsoluteInjector(): CommandTempBasalAbsolute
        @ContributesAndroidInjector fun commandTempBasalPercentInjector(): CommandTempBasalPercent
        @ContributesAndroidInjector fun commandSetUserSettingsInjector(): CommandSetUserSettings
        @ContributesAndroidInjector fun commandCustomCommandInjector(): CommandCustomCommand
    }

    @Provides
    fun commandQueueJobName(): CommandQueueName = CommandQueueName("CommandQueue")
}