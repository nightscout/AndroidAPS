package app.aaps.plugins.configuration.di

import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.protection.PasswordCheck
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.crypto.CryptoUtil
import app.aaps.plugins.configuration.setupwizard.SWEventListener
import app.aaps.plugins.configuration.setupwizard.SWScreen
import app.aaps.plugins.configuration.setupwizard.SetupWizardActivity
import app.aaps.plugins.configuration.setupwizard.elements.SWBreak
import app.aaps.plugins.configuration.setupwizard.elements.SWButton
import app.aaps.plugins.configuration.setupwizard.elements.SWEditEncryptedPassword
import app.aaps.plugins.configuration.setupwizard.elements.SWEditIntNumber
import app.aaps.plugins.configuration.setupwizard.elements.SWEditNumber
import app.aaps.plugins.configuration.setupwizard.elements.SWEditNumberWithUnits
import app.aaps.plugins.configuration.setupwizard.elements.SWEditString
import app.aaps.plugins.configuration.setupwizard.elements.SWEditUrl
import app.aaps.plugins.configuration.setupwizard.elements.SWFragment
import app.aaps.plugins.configuration.setupwizard.elements.SWHtmlLink
import app.aaps.plugins.configuration.setupwizard.elements.SWInfoText
import app.aaps.plugins.configuration.setupwizard.elements.SWItem
import app.aaps.plugins.configuration.setupwizard.elements.SWPlugin
import app.aaps.plugins.configuration.setupwizard.elements.SWRadioButton
import dagger.Module
import dagger.Provides
import dagger.android.ContributesAndroidInjector

@Module(
    includes = [
        SetupWizardModule.Provide::class
    ]
)
@Suppress("unused")
abstract class SetupWizardModule {

    @ContributesAndroidInjector abstract fun contributesSetupWizardActivity(): SetupWizardActivity

    @Module
    class Provide {

        @Provides
        fun providesSWScreen(rh: ResourceHelper) = SWScreen(rh)
        fun providesSWItem(aapsLogger: AAPSLogger, rh: ResourceHelper, rxBus: RxBus, preferences: Preferences, passwordCheck: PasswordCheck) =
            SWItem(aapsLogger, rh, rxBus, preferences, passwordCheck)

        fun providesSWBreak(aapsLogger: AAPSLogger, rh: ResourceHelper, rxBus: RxBus, preferences: Preferences, passwordCheck: PasswordCheck) =
            SWBreak(aapsLogger, rh, rxBus, preferences, passwordCheck)

        fun providesSWButton(aapsLogger: AAPSLogger, rh: ResourceHelper, rxBus: RxBus, preferences: Preferences, passwordCheck: PasswordCheck) =
            SWButton(aapsLogger, rh, rxBus, preferences, passwordCheck)

        fun providesSWEditEncryptedPassword(aapsLogger: AAPSLogger, rh: ResourceHelper, rxBus: RxBus, preferences: Preferences, passwordCheck: PasswordCheck, cryptoUtil: CryptoUtil) =
            SWEditEncryptedPassword(aapsLogger, rh, rxBus, preferences, passwordCheck, cryptoUtil)

        fun providesSWEditIntNumber(aapsLogger: AAPSLogger, rh: ResourceHelper, rxBus: RxBus, preferences: Preferences, passwordCheck: PasswordCheck) =
            SWEditIntNumber(aapsLogger, rh, rxBus, preferences, passwordCheck)

        fun providesSWEditNumber(aapsLogger: AAPSLogger, rh: ResourceHelper, rxBus: RxBus, preferences: Preferences, passwordCheck: PasswordCheck) =
            SWEditNumber(aapsLogger, rh, rxBus, preferences, passwordCheck)

        fun providesSWEditNumberWithUnits(aapsLogger: AAPSLogger, rh: ResourceHelper, rxBus: RxBus, preferences: Preferences, passwordCheck: PasswordCheck, profileUtil: ProfileUtil) =
            SWEditNumberWithUnits(aapsLogger, rh, rxBus, preferences, passwordCheck, profileUtil)

        fun providesSWEditString(aapsLogger: AAPSLogger, rh: ResourceHelper, rxBus: RxBus, preferences: Preferences, passwordCheck: PasswordCheck) =
            SWEditString(aapsLogger, rh, rxBus, preferences, passwordCheck)

        fun providesSWEditUrl(aapsLogger: AAPSLogger, rh: ResourceHelper, rxBus: RxBus, preferences: Preferences, passwordCheck: PasswordCheck) =
            SWEditUrl(aapsLogger, rh, rxBus, preferences, passwordCheck)

        fun providesSWEventListener(aapsLogger: AAPSLogger, rh: ResourceHelper, rxBus: RxBus, preferences: Preferences, passwordCheck: PasswordCheck) =
            SWEventListener(aapsLogger, rh, rxBus, preferences, passwordCheck)

        fun providesSWFragment(aapsLogger: AAPSLogger, rh: ResourceHelper, rxBus: RxBus, preferences: Preferences, passwordCheck: PasswordCheck) =
            SWFragment(aapsLogger, rh, rxBus, preferences, passwordCheck)

        fun providesSWHtmlLink(aapsLogger: AAPSLogger, rh: ResourceHelper, rxBus: RxBus, preferences: Preferences, passwordCheck: PasswordCheck) =
            SWHtmlLink(aapsLogger, rh, rxBus, preferences, passwordCheck)

        fun providesSWInfoText(aapsLogger: AAPSLogger, rh: ResourceHelper, rxBus: RxBus, preferences: Preferences, passwordCheck: PasswordCheck) =
            SWInfoText(aapsLogger, rh, rxBus, preferences, passwordCheck)

        fun providesSWPlugin(
            aapsLogger: AAPSLogger, rh: ResourceHelper, rxBus: RxBus, preferences: Preferences, passwordCheck: PasswordCheck, activePlugin: ActivePlugin, configBuilder: ConfigBuilder
        ) = SWPlugin(aapsLogger, rh, rxBus, preferences, passwordCheck, activePlugin, configBuilder)

        fun providesSWRadioButton(aapsLogger: AAPSLogger, rh: ResourceHelper, rxBus: RxBus, preferences: Preferences, passwordCheck: PasswordCheck) =
            SWRadioButton(aapsLogger, rh, rxBus, preferences, passwordCheck)
    }
}