package app.aaps.plugins.sync.tidepool

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import androidx.core.view.MenuCompat
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.sync.R
import app.aaps.plugins.sync.databinding.TidepoolFragmentBinding
import app.aaps.plugins.sync.tidepool.auth.AuthFlowOut
import app.aaps.plugins.sync.tidepool.comm.TidepoolUploader
import app.aaps.plugins.sync.tidepool.events.EventTidepoolDoUpload
import app.aaps.plugins.sync.tidepool.events.EventTidepoolUpdateGUI
import app.aaps.plugins.sync.tidepool.keys.TidepoolLongNonKey
import dagger.android.support.DaggerFragment
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject

class TidepoolFragment : DaggerFragment(), MenuProvider {

    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var tidepoolPlugin: TidepoolPlugin
    @Inject lateinit var tidepoolUploader: TidepoolUploader
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var authFlowOut: AuthFlowOut

    companion object {

        const val ID_MENU_LOGIN = 530
        const val ID_MENU_LOGOUT = 531
        const val ID_MENU_SEND_NOW = 532
        const val ID_MENU_FULL_SYNC = 534
    }

    private var disposable: CompositeDisposable = CompositeDisposable()

    private var _binding: TidepoolFragmentBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = TidepoolFragmentBinding.inflate(inflater, container, false)
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
        return binding.root
    }

    override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
        menu.add(Menu.FIRST, ID_MENU_LOGIN, 0, rh.gs(app.aaps.core.ui.R.string.login)).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add(Menu.FIRST, ID_MENU_LOGOUT, 0, rh.gs(app.aaps.core.ui.R.string.logout)).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add(Menu.FIRST, ID_MENU_SEND_NOW, 0, rh.gs(R.string.upload_now)).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add(Menu.FIRST, ID_MENU_FULL_SYNC, 0, rh.gs(R.string.full_sync)).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        MenuCompat.setGroupDividerEnabled(menu, true)
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            ID_MENU_LOGIN      -> {
                authFlowOut.doTidePoolInitialLogin("menu")
                true
            }

            ID_MENU_LOGOUT      -> {
                authFlowOut.clearAllSavedData()
                tidepoolUploader.resetInstance()
                true
            }

            ID_MENU_SEND_NOW   -> {
                rxBus.send(EventTidepoolDoUpload())
                true
            }

            ID_MENU_FULL_SYNC  -> {
                preferences.put(TidepoolLongNonKey.LastEnd, 0)
                true
            }

            else               -> false
        }

    @Synchronized
    override fun onResume() {
        super.onResume()
        disposable += rxBus
            .toObservable(EventTidepoolUpdateGUI::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateGui() }, fabricPrivacy::logException)
        updateGui()
    }

    private fun updateGui() {
        tidepoolPlugin.updateLog()
        _binding?.log?.text = tidepoolPlugin.textLog
        _binding?.status?.text = authFlowOut.connectionStatus.name
        _binding?.log?.text = tidepoolPlugin.textLog
        _binding?.logScrollview?.fullScroll(ScrollView.FOCUS_DOWN)
    }

    @Synchronized
    override fun onPause() {
        super.onPause()
        disposable.clear()
    }

    @Synchronized
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}