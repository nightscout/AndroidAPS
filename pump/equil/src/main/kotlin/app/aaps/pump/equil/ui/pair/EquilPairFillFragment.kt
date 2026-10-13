package app.aaps.pump.equil.ui.pair

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.fragment.app.DialogFragment
import androidx.navigation.fragment.findNavController
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.ui.extensions.runOnUiThread
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.pump.equil.EquilConst
import app.aaps.pump.equil.R
import app.aaps.pump.equil.database.EquilHistoryRecord
import app.aaps.pump.equil.database.ResolvedResult
import app.aaps.pump.equil.manager.command.CmdResistanceGet
import app.aaps.pump.equil.manager.command.CmdStepSet
import app.aaps.pump.equil.ui.dlg.EquilAutoDressingDlg
import javax.inject.Inject

class EquilPairFillFragment : EquilPairFragmentBase() {

    @Inject lateinit var profileFunction: ProfileFunction

    override fun getLayoutId(): Int {
        return R.layout.equil_pair_fill_fragment
    }

    override fun getNextPageActionId(): Int {
        return R.id.action_startEquilActivationFragment_to_startEquilPairAttachFragment
    }

    override fun getIndex(): Int {
        return 3
    }

    var auto: Boolean = false
    private var buttonFill: Button? = null
    private var buttonFinish: Button? = null
    private var lytAction: View? = null
    var intStep = 0

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        buttonFill = view.findViewById(R.id.button_fill)
        buttonFinish = view.findViewById(R.id.button_finish)
        lytAction = view.findViewById(R.id.lyt_action)
        view.findViewById<Button>(R.id.button_next)?.let { buttonNext ->
            buttonNext.alpha = 0.3f
            buttonNext.isClickable = false
        }
        buttonFill?.setOnClickListener {
            context?.let {
                aapsLogger.info(LTag.PUMPCOMM, "EquilFill: User initiated auto-fill, starting pin movement from step=$intStep")
                auto = true
                showAutoDlg()
                setStep()
            }
        }
        buttonFinish?.setOnClickListener {
            context?.let {
                val time = System.currentTimeMillis()
                val equilHistoryRecord = EquilHistoryRecord(
                    id = time,
                    type = EquilHistoryRecord.EventType.FILL,
                    timestamp = time,
                    serialNumber = equilPumpPlugin.serialNumber(),
                    resolvedAt = System.currentTimeMillis(),
                    resolvedStatus = ResolvedResult.SUCCESS
                )
                equilPumpPlugin.handler?.post {
                    equilHistoryRecordDao.insert(equilHistoryRecord)
                }
                val nextPage = getNextPageActionId()
                findNavController().navigate(nextPage)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Remove listeners first (breaks View → Fragment reference)
        buttonFill?.setOnClickListener(null)
        buttonFinish?.setOnClickListener(null)
        // Then null references (breaks Fragment → View reference)
        lytAction = null
        buttonFill = null
        buttonFinish = null
    }

    private fun showAutoDlg() {
        val dialogFragment = EquilAutoDressingDlg()
        dialogFragment.setDialogResultListener {
            auto = false
        }
        dialogFragment.show(childFragmentManager, "autoDlg")
    }

    private fun dismissAutoDlg() {
        val fragment = childFragmentManager.findFragmentByTag("autoDlg")
        if (fragment is DialogFragment) {
            try {
                fragment.dismiss()
            } catch (e: IllegalStateException) {
                // dialog not running yet
                aapsLogger.error("Unhandled exception", e)
            }
        }
    }

    private fun setStep() {
        aapsLogger.debug(LTag.PUMPCOMM, "EquilFill: setStep() called, currentStep=$intStep, stepIncrement=${EquilConst.EQUIL_STEP_FILL}, maxStep=${EquilConst.EQUIL_STEP_MAX}")
        commandQueue.customCommand(CmdStepSet(false, EquilConst.EQUIL_STEP_FILL, aapsLogger, preferences, equilManager), object : Callback() {
            override fun run() {
                if (activity == null) return
                aapsLogger.debug(LTag.PUMPCOMM, "EquilFill: CmdStepSet result.success=${result.success}, intStep=$intStep")
                if (result.success) {
                    intStep += EquilConst.EQUIL_STEP_FILL
                    aapsLogger.debug(LTag.PUMPCOMM, "EquilFill: Pin moved, newTotalStep=$intStep (${intStep / EquilConst.EQUIL_STEP_FILL} iterations)")
                    readStatus()
                } else {
                    aapsLogger.warn(LTag.PUMPCOMM, "EquilFill: CmdStepSet FAILED at step $intStep")
                    if (auto) dismissAutoDlg()
                    else dismissLoading()
                }
            }
        })
    }

    private fun readStatus() {
        aapsLogger.debug(LTag.PUMPCOMM, "EquilFill: readStatus() checking resistance at step $intStep")
        commandQueue.customCommand(
            CmdResistanceGet(aapsLogger, preferences, equilManager),
            object : Callback() {
                override fun run() {
                    if (activity == null) return
                    aapsLogger.debug(LTag.PUMPCOMM, "EquilFill: readStatus result.success=${result.success}, result.enacted=${result.enacted}, intStep=$intStep, auto=$auto")
                    // result.enacted=true means pin reached piston (resistance >= 500)
                    if (result.success) {
                        if (!result.enacted) {
                            aapsLogger.debug(LTag.PUMPCOMM, "EquilFill: Pin NOT at piston yet, continuing...")
                            if (auto) {
                                if (intStep > EquilConst.EQUIL_STEP_MAX) {
                                    aapsLogger.error(LTag.PUMPCOMM, "EquilFill: MAXIMUM STEP EXCEEDED! intStep=$intStep > maxStep=${EquilConst.EQUIL_STEP_MAX}")
                                    ToastUtils.infoToast(context, rh.gs(R.string.equil_replace_reservoir))
                                    dismissLoading()
                                    activity?.finish()
                                    return
                                }
                                setStep()
                            } else {
                                aapsLogger.debug(LTag.PUMPCOMM, "EquilFill: Manual mode, dismissing loading")
                                dismissLoading()
                            }
                        } else {
                            aapsLogger.info(LTag.PUMPCOMM, "EquilFill: Pin REACHED piston at step $intStep (${intStep / EquilConst.EQUIL_STEP_FILL} iterations), stopping fill")
                            if (auto) {
                                runOnUiThread {
                                    buttonFill?.visibility = View.GONE
                                    lytAction?.visibility = View.VISIBLE
                                }
                                dismissAutoDlg()
                            } else dismissLoading()
                        }
                    } else {
                        aapsLogger.warn(LTag.PUMPCOMM, "EquilFill: CmdResistanceGet FAILED at step $intStep")
                        if (auto) dismissAutoDlg()
                        else dismissLoading()
                    }
                }
            }
        )
    }
}
