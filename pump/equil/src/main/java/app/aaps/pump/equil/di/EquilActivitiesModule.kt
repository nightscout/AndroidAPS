package app.aaps.pump.equil.di

import app.aaps.pump.equil.EquilFragment
import app.aaps.pump.equil.ui.*
import app.aaps.pump.equil.ui.dlg.*
import app.aaps.pump.equil.ui.pair.*
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
@Suppress("unused")
abstract class EquilActivitiesModule {

    @ContributesAndroidInjector abstract fun contributesEquilFragment(): EquilFragment
    @ContributesAndroidInjector abstract fun contributesEquilChangeInsulinDlg(): EquilChangeInsulinDlg
    @ContributesAndroidInjector abstract fun contributesLoadingDlg(): LoadingDlg
    @ContributesAndroidInjector abstract fun contributesSingleChooseDlg(): SingleChooseDlg
    @ContributesAndroidInjector abstract fun contributesNumberChooseDlg(): NumberChooseDlg
    @ContributesAndroidInjector abstract fun contributesEquilUnPairDlg(): EquilUnPairDlg
    @ContributesAndroidInjector abstract fun contributesEquilPairConfigDlg(): EquilPairConfigDlg
    @ContributesAndroidInjector abstract fun contributesEquilAutoDressingDlg(): EquilAutoDressingDlg

    @ContributesAndroidInjector abstract fun contributesEquilUnPairDetachActivity(): EquilUnPairDetachActivity
    @ContributesAndroidInjector abstract fun contributesEquilUnPairActivity(): EquilUnPairActivity
    @ContributesAndroidInjector abstract fun contributesEquilPairActivity(): EquilPairActivity
    @ContributesAndroidInjector abstract fun contributesEquilPairFragment(): EquilPairFragmentBase
    @ContributesAndroidInjector abstract fun contributesEquilPairAssembleFragment(): EquilPairAssembleFragment
    @ContributesAndroidInjector abstract fun contributesEquilPairSerialNumberFragment(): EquilPairSerialNumberFragment
    @ContributesAndroidInjector abstract fun contributesEquilPairFillFragment(): EquilPairFillFragment
    @ContributesAndroidInjector abstract fun contributesEquilPairConfirmFragment(): EquilPairConfirmFragment
    @ContributesAndroidInjector abstract fun contributesEquilPairAttachFragment(): EquilPairAttachFragment
    @ContributesAndroidInjector abstract fun contributesEquilPairAirFragment(): EquilPairAirFragment
    @ContributesAndroidInjector abstract fun contributesEquilChangeInsulinFragment(): EquilChangeInsulinFragment

    @ContributesAndroidInjector abstract fun contributesEquilHistoryRecordActivity(): EquilHistoryRecordActivity

    // @ContributesAndroidInjector abstract fun contributesEquilChangeActivity(): EquilChangeActivity

}