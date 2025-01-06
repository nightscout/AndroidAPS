package app.aaps.plugins.configuration.di

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
import dagger.android.ContributesAndroidInjector

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
    @ContributesAndroidInjector abstract fun swHtmlLinkInjector(): SWHtmlLink
    @ContributesAndroidInjector abstract fun swInfoTextInjector(): SWInfoText
    @ContributesAndroidInjector abstract fun swItemInjector(): SWItem
    @ContributesAndroidInjector abstract fun swPluginInjector(): SWPlugin
    @ContributesAndroidInjector abstract fun swRadioButtonInjector(): SWRadioButton
    @ContributesAndroidInjector abstract fun swScreenInjector(): SWScreen
    @ContributesAndroidInjector abstract fun swEventListenerInjector(): SWEventListener
}