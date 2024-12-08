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

// IMPORTANT: This activity needs to be called from RileyLinkSelectPreference (see pref_medtronic.xml as example)
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
    private lateinit var buttonNext: Button
    lateinit var buttonFill: Button
    lateinit var lytAction: View
    var intStep = 0

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        buttonNext = view.findViewById(R.id.button_next)
        buttonFill = view.findViewById(R.id.button_fill)
        lytAction = view.findViewById(R.id.lyt_action)
        buttonNext.alpha = 0.3f
        buttonNext.isClickable = false
        buttonFill.setOnClickListener {
            context?.let {
                auto = true
                showAutoDlg()
                setStep()
            }
        }
        view.findViewById<Button>(R.id.button_finish).setOnClickListener {
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

    private fun showAutoDlg() {
        val dialogFragment = EquilAutoDressingDlg()
        dialogFragment.setDialogResultListener {
            // binding.tvLimitReservoir.text = result.toString()
            // changeInsulin()
            auto = false
            // equilPumpPlugin.equilManager.closeBle();
        }
        dialogFragment.show(childFragmentManager, "autoDlg")

        // EquilAutoDressingDlg().also { dialog ->
        // }.show(supportFragmentManager, "autoDlg")
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
        commandQueue.customCommand(CmdStepSet(false, EquilConst.EQUIL_STEP_FILL, aapsLogger, sp, equilManager), object : Callback() {
            override fun run() {
                if (activity == null) return

                aapsLogger.debug(LTag.PUMPCOMM, "result====" + result.success)
                if (result.success) {
                    // SystemClock.sleep(EquilConst.EQUIL_BLE_NEXT_CMD)
                    intStep += EquilConst.EQUIL_STEP_FILL
                    readStatus()
                } else {
                    if (auto) {
                        dismissAutoDlg()
                    } else {
                        dismissLoading()
                    }
                }
            }
        })
    }

    private fun readStatus() {
        commandQueue.customCommand(
            CmdResistanceGet(aapsLogger, sp, equilManager),
            object : Callback() {
                override fun run() {
                    if (activity == null) return
                    aapsLogger.debug(LTag.PUMPCOMM, "readStatus result====" + result.success + "===" + result.enacted + "====" + auto)
                    // result.enacted=true
                    if (result.success) {
                        if (!result.enacted) {
                            if (auto) {
                                if (intStep > EquilConst.EQUIL_STEP_MAX) {
                                    ToastUtils.infoToast(context, rh.gs(R.string.equil_replace_reservoir))
                                    dismissLoading()
                                    activity?.finish()
                                    return
                                }
                                setStep()
                            } else {
                                dismissLoading()
                            }
                        } else {
                            if (auto) {
                                runOnUiThread {
                                    buttonFill.visibility = View.GONE
                                    lytAction.visibility = View.VISIBLE
                                }
                                dismissAutoDlg()
                            } else {
                                dismissLoading()
                            }
                        }
                    } else {
                        if (auto) {
                            dismissAutoDlg()
                        } else {
                            dismissLoading()
                        }

                    }
                }
            }
        )
    }

}
