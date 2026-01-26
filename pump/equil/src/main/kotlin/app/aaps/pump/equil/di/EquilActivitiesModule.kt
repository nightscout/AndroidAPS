package app.aaps.pump.equil.di

import app.aaps.pump.equil.EquilFragment
import app.aaps.pump.equil.ui.EquilHistoryRecordActivity
import app.aaps.pump.equil.ui.EquilUnPairActivity
import app.aaps.pump.equil.ui.EquilUnPairDetachActivity
import app.aaps.pump.equil.ui.dlg.EquilAutoDressingDlg
import app.aaps.pump.equil.ui.dlg.LoadingDlg
import app.aaps.pump.equil.ui.pair.EquilChangeInsulinFragment
import app.aaps.pump.equil.ui.pair.EquilPairActivity
import app.aaps.pump.equil.ui.pair.EquilPairAirFragment
import app.aaps.pump.equil.ui.pair.EquilPairAssembleFragment
import app.aaps.pump.equil.ui.pair.EquilPairAttachFragment
import app.aaps.pump.equil.ui.pair.EquilPairConfirmFragment
import app.aaps.pump.equil.ui.pair.EquilPairFragmentBase
import app.aaps.pump.equil.ui.pair.EquilPairSerialNumberFragment
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
@Suppress("unused")
abstract class EquilActivitiesModule {

    @ContributesAndroidInjector abstract fun contributesEquilFragment(): EquilFragment
    @ContributesAndroidInjector abstract fun contributesLoadingDlg(): LoadingDlg
    @ContributesAndroidInjector abstract fun contributesEquilAutoDressingDlg(): EquilAutoDressingDlg

    @ContributesAndroidInjector abstract fun contributesEquilUnPairDetachActivity(): EquilUnPairDetachActivity
    @ContributesAndroidInjector abstract fun contributesEquilUnPairActivity(): EquilUnPairActivity
    @ContributesAndroidInjector abstract fun contributesEquilPairActivity(): EquilPairActivity
    @ContributesAndroidInjector abstract fun contributesEquilPairFragment(): EquilPairFragmentBase
    @ContributesAndroidInjector abstract fun contributesEquilPairAssembleFragment(): EquilPairAssembleFragment
    @ContributesAndroidInjector abstract fun contributesEquilPairSerialNumberFragment(): EquilPairSerialNumberFragment
    @ContributesAndroidInjector abstract fun contributesEquilPairConfirmFragment(): EquilPairConfirmFragment
    @ContributesAndroidInjector abstract fun contributesEquilPairAttachFragment(): EquilPairAttachFragment
    @ContributesAndroidInjector abstract fun contributesEquilPairAirFragment(): EquilPairAirFragment
    @ContributesAndroidInjector abstract fun contributesEquilChangeInsulinFragment(): EquilChangeInsulinFragment

    @ContributesAndroidInjector abstract fun contributesEquilHistoryRecordActivity(): EquilHistoryRecordActivity
}