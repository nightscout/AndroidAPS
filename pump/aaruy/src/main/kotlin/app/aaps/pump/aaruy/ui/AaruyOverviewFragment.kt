package app.aaps.pump.aaruy.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModelProvider
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.pump.WarnColors
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.pump.aaruy.AaruyPump
import app.aaps.pump.aaruy.R
import app.aaps.pump.aaruy.bindingadapters.setOnClickListener
import app.aaps.pump.aaruy.code.EventType
import app.aaps.pump.aaruy.code.ReplaceStep
import app.aaps.pump.aaruy.databinding.FragmentAaruyOverviewBinding
import app.aaps.pump.aaruy.ui.viewmodel.AaruyOverviewViewModel
import io.reactivex.rxjava3.disposables.CompositeDisposable
import javax.inject.Inject

class AaruyOverviewFragment : AaruyBaseFragment<FragmentAaruyOverviewBinding>() {

    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var aaruyPump: AaruyPump
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var warnColors: WarnColors
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var uiInteraction: UiInteraction
    @Inject lateinit var protectionCheck: ProtectionCheck

    private var disposable: CompositeDisposable = CompositeDisposable()

    override fun getLayoutId(): Int = R.layout.fragment_aaruy_overview

    override fun onDestroy() {
        super.onDestroy()
        disposable.clear()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.apply {
            viewmodel = ViewModelProvider(this@AaruyOverviewFragment, viewModelFactory)[AaruyOverviewViewModel::class.java]
            viewmodel?.apply {
                eventHandler.observe(viewLifecycleOwner) { evt ->
                    when (evt.peekContent()) {
                        EventType.CHANGE_REPLACE_CLICKED -> {
                            val nextStep = ReplaceStep.REPLACE_STEP_1
                            context?.let { AaruyActivity.createIntentFromMenu(it, nextStep) }?.let { startActivity(it) }
                        }

                        EventType.PROFILE_NOT_SET      -> {
                            OKDialog.show(requireActivity(), rh.gs(app.aaps.core.ui.R.string.message), rh.gs(R.string.no_profile_selected))
                        }

                        EventType.SERIAL_NOT_SET       -> {
                            OKDialog.show(requireActivity(), rh.gs(app.aaps.core.ui.R.string.message), rh.gs(R.string.no_sn_in_settings))
                        }

                        EventType.VIEW_HISTORY_CLICKED   -> {
                            startActivity(Intent(context, AaruyHistoryLogActivity::class.java))
                        }
                    }
                }

                binding.refreshButton.setOnClickListener {
                    onClickRefresh()
                }

                binding.history.setOnClickListener {
                    onClickViewHistory()
                }

                binding.resetAlarmsButton.setOnClickListener {
                    onClickResetAlarms()
                }

                binding.changePatchButton.setOnClickListener {
                    onClickReplaceResvBat()
                }

                binding.reconnectButton.setOnClickListener {
                    onClickReconnect()
                }
            }
        }
    }
}