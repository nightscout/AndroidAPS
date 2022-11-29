package info.nightscout.configuration.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.configuration.setupwizard.SWEventListener
import info.nightscout.configuration.setupwizard.SWScreen
import info.nightscout.configuration.setupwizard.SetupWizardActivity
import info.nightscout.configuration.setupwizard.elements.SWBreak
import info.nightscout.configuration.setupwizard.elements.SWButton
import info.nightscout.configuration.setupwizard.elements.SWEditEncryptedPassword
import info.nightscout.configuration.setupwizard.elements.SWEditIntNumber
import info.nightscout.configuration.setupwizard.elements.SWEditNumber
import info.nightscout.configuration.setupwizard.elements.SWEditNumberWithUnits
import info.nightscout.configuration.setupwizard.elements.SWEditString
import info.nightscout.configuration.setupwizard.elements.SWEditUrl
import info.nightscout.configuration.setupwizard.elements.SWFragment
import info.nightscout.configuration.setupwizard.elements.SWHtmlLink
import info.nightscout.configuration.setupwizard.elements.SWInfoText
import info.nightscout.configuration.setupwizard.elements.SWItem
import info.nightscout.configuration.setupwizard.elements.SWPlugin
import info.nightscout.configuration.setupwizard.elements.SWPreference
import info.nightscout.configuration.setupwizard.elements.SWRadioButton

@Module
@Suppress("unused")
abstract class SetupWizardModule {

    @ContributesAndroidInjector abstract fun contributesSetupWizardActivity(): SetupWizardActivity

    @ContributesAndroidInjector abstract fun swBreakInjector(): SWBreak
    @ContributesAndroidInjector abstract fun swButtonInjector(): SWButton
    @ContributesAndroidInjector abstract fun swEditNumberWithUnitsInjector(): SWEditNumberWithUnits
    @ContributesAndroidInjector abstract fun swEditNumberInjector(): SWEditNumber
    @ContributesAndroidInjector abstract fun swEditIntNumberInjector(): SWEditIntNumber
    @ContributesAndroidInjector abstract fun swEditStringInjector(): SWEditString
    @ContributesAndroidInjector abstract fun swEditEncryptedPasswordInjector(): SWEditEncryptedPassword
    @ContributesAndroidInjector abstract fun swEditUrlInjector(): SWEditUrl
    @ContributesAndroidInjector abstract fun swFragmentInjector(): SWFragment
    @ContributesAndroidInjector abstract fun swPreferenceInjector(): SWPreference
    @ContributesAndroidInjector abstract fun swHtmlLinkInjector(): SWHtmlLink
    @ContributesAndroidInjector abstract fun swInfoTextInjector(): SWInfoText
    @ContributesAndroidInjector abstract fun swItemInjector(): SWItem
    @ContributesAndroidInjector abstract fun swPluginInjector(): SWPlugin
    @ContributesAndroidInjector abstract fun swRadioButtonInjector(): SWRadioButton
    @ContributesAndroidInjector abstract fun swScreenInjector(): SWScreen
    @ContributesAndroidInjector abstract fun swEventListenerInjector(): SWEventListener
}