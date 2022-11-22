package info.nightscout.implementation.di

import android.content.Context
import dagger.Lazy
import dagger.Module
import dagger.Provides
import info.nightscout.androidaps.plugins.constraints.versionChecker.VersionCheckerUtils
import info.nightscout.androidaps.plugins.general.maintenance.formats.EncryptedPrefsFormat
import info.nightscout.core.graph.OverviewData
import info.nightscout.core.utils.CryptoUtil
import info.nightscout.core.utils.fabric.FabricPrivacy
import info.nightscout.database.impl.AppRepository
import info.nightscout.implementation.DefaultValueHelperImpl
import info.nightscout.implementation.HardLimitsImpl
import info.nightscout.implementation.TranslatorImpl
import info.nightscout.implementation.logging.LoggerUtilsImpl
import info.nightscout.implementation.maintenance.PrefFileListProviderImpl
import info.nightscout.implementation.overview.OverviewDataImpl
import info.nightscout.implementation.profiling.ProfilerImpl
import info.nightscout.implementation.protection.PasswordCheckImpl
import info.nightscout.implementation.protection.ProtectionCheckImpl
import info.nightscout.implementation.pump.WarnColorsImpl
import info.nightscout.implementation.resources.ResourceHelperImpl
import info.nightscout.interfaces.Config
import info.nightscout.interfaces.Translator
import info.nightscout.interfaces.logging.LoggerUtils
import info.nightscout.interfaces.maintenance.PrefFileListProvider
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.profile.DefaultValueHelper
import info.nightscout.interfaces.profile.ProfileFunction
import info.nightscout.interfaces.profiling.Profiler
import info.nightscout.interfaces.protection.PasswordCheck
import info.nightscout.interfaces.protection.ProtectionCheck
import info.nightscout.interfaces.pump.WarnColors
import info.nightscout.interfaces.storage.Storage
import info.nightscout.interfaces.utils.HardLimits
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.DateUtil
import javax.inject.Singleton

@Module(
    includes = [
        CommandQueueModule::class
    ]
)

@Suppress("unused")
open class ImplementationModule {

    @Provides
    @Singleton
    fun provideResources(context: Context, fabricPrivacy: FabricPrivacy): ResourceHelper =
        ResourceHelperImpl(context, fabricPrivacy)

    @Provides
    @Singleton
    fun provideHardLimits(aapsLogger: AAPSLogger, rxBus: RxBus, sp: SP, rh: ResourceHelper, context: Context, repository: AppRepository): HardLimits =
        HardLimitsImpl(aapsLogger, rxBus, sp, rh, context, repository)

    @Provides
    @Singleton
    fun provideWarnColors(rh: ResourceHelper): WarnColors = WarnColorsImpl(rh)

    @Provides
    @Singleton
    fun provideProfiler(aapsLogger: AAPSLogger): Profiler = ProfilerImpl(aapsLogger)

    @Provides
    @Singleton
    fun provideLoggerUtils(prefFileListProvider: PrefFileListProvider): LoggerUtils = LoggerUtilsImpl(prefFileListProvider)

    @Provides
    @Singleton
    fun providePasswordCheck(sp: SP, cryptoUtil: CryptoUtil): PasswordCheck = PasswordCheckImpl(sp, cryptoUtil)

    @Provides
    @Singleton
    fun provideProtectionCheck(sp: SP, passwordCheck: PasswordCheck, dateUtil: DateUtil): ProtectionCheck = ProtectionCheckImpl(sp, passwordCheck, dateUtil)

    @Provides
    @Singleton
    fun provideDefaultValueHelper(sp: SP, profileFunction: ProfileFunction): DefaultValueHelper = DefaultValueHelperImpl(sp, profileFunction)

    @Provides
    @Singleton
    fun provideTranslator(rh: ResourceHelper): Translator = TranslatorImpl(rh)

    @Provides
    @Singleton
    fun provideOverviewData(
        aapsLogger: AAPSLogger,
        rh: ResourceHelper,
        dateUtil: DateUtil,
        sp: SP,
        activePlugin: ActivePlugin,
        defaultValueHelper: DefaultValueHelper,
        profileFunction: ProfileFunction,
        repository: AppRepository
    ): OverviewData = OverviewDataImpl(aapsLogger, rh, dateUtil, sp, activePlugin, defaultValueHelper, profileFunction, repository)

    @Provides
    @Singleton
    fun providePrefFileListProvider(
        rh: ResourceHelper,
        config: Lazy<Config>,
        encryptedPrefsFormat: EncryptedPrefsFormat,
        storage: Storage,
        versionCheckerUtils: VersionCheckerUtils,
        context: Context
    ): PrefFileListProvider = PrefFileListProviderImpl(rh, config, encryptedPrefsFormat, storage, versionCheckerUtils, context)
}