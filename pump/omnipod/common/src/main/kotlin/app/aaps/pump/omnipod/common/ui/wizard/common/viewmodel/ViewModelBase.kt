package app.aaps.pump.omnipod.common.ui.wizard.common.viewmodel

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel

abstract class ViewModelBase : ViewModel() {

    @StringRes abstract fun getTitleId(): Int

    @StringRes abstract fun getTextId(): Int
}