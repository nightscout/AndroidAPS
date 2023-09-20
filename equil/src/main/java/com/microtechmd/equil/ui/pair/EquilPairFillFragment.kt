package com.microtechmd.equil.ui.pair

import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.Button
import androidx.fragment.app.DialogFragment
import androidx.navigation.fragment.findNavController
import com.microtechmd.equil.R
import com.microtechmd.equil.data.RunMode
import com.microtechmd.equil.data.database.EquilHistoryRecord
import com.microtechmd.equil.manager.command.CmdBasalSet
import com.microtechmd.equil.manager.command.CmdDevicesGet
import com.microtechmd.equil.manager.command.CmdResistanceGet
import com.microtechmd.equil.manager.command.CmdModelSet
import com.microtechmd.equil.manager.command.CmdStepSet
import com.microtechmd.equil.manager.command.CmdTimeSet
import com.microtechmd.equil.ui.dlg.EquilAutoDressingDlg
import info.nightscout.androidaps.extensions.runOnUiThread
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.queue.Callback
import info.nightscout.shared.logging.LTag
import javax.inject.Inject

// IMPORTANT: This activity needs to be called from RileyLinkSelectPreference (see pref_medtronic.xml as example)
class EquilPairFillFragment : EquilPairFragmentBase() {

    @Inject lateinit var profileFunction: ProfileFunction

    override fun getLayoutId(): Int {
        return R.layout.equil_pair_fill_fragment
    }

    override fun getNextPageActionId(): Int? {
        return R.id.action_startEquilActivationFragment_to_startEquilPairConfirmFragment;
    }

    override fun getIndex(): Int {
        if ((activity as? EquilPairActivity)?.pair == false) {
            return 1
        }
        return 3
    }

    var auto: Boolean = false
    lateinit var buttonNext: Button
    lateinit var buttonFill: Button
    lateinit var lytAction: View

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        buttonNext = view.findViewById<Button>(R.id.button_next);
        buttonFill = view.findViewById<Button>(R.id.button_fill);
        lytAction = view.findViewById<View>(R.id.lyt_action);
        buttonNext.alpha = 0.3f
        buttonNext.isClickable = false
        // view.findViewById<Button>(R.id.button_next).setOnClickListener {
        //     context?.let {
        //         showLoading()
        //         setProfile()
        //     }
        // }
        buttonFill.setOnClickListener {
            context?.let {
                auto = true
                showAutoDlg()
                setStep()
            }
        }
        view.findViewById<Button>(R.id.button_air).setOnClickListener {
            context?.let {
                auto = false
                showLoading()
                setStep()
            }
        }
        view.findViewById<Button>(R.id.button_finish).setOnClickListener {
            context?.let {
                auto = true
                showLoading()
                if ((activity as? EquilPairActivity)?.pair == true) {
                    setProfile()
                } else {
                    setModel()
                }
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
            fragment.dismiss()
        }
    }

    private fun setStep() {
        commandQueue.customCommand(CmdStepSet(), object : Callback() {
            override fun run() {
                aapsLogger.error(LTag.EQUILBLE, "result====" + result.success)
                if (result.success) {
                    if (auto) {
                        SystemClock.sleep(50)
                        readStatus()
                    } else {
                        SystemClock.sleep(50)
                        readStatus();
                    }
                } else {
                    if (auto) {
                        dismissAutoDlg();
                    } else {
                        dismissLoading()
                    }
                    // equilPumpPlugin.showToast(rh.gs(R.string.equil_error))
                }
            }
        })
    }

    private fun readStatus() {
        commandQueue.customCommand(
            CmdResistanceGet(),
            object : Callback() {
                override fun run() {
                    aapsLogger.error(LTag.EQUILBLE, "readStatus result====" + result.success + "===" + result.enacted + "====" + auto)
                    if (result.success) {
                        if (!result.enacted) {
                            if (auto) {
                                SystemClock.sleep(50)
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
                                dismissAutoDlg();
                            } else {
                                dismissLoading()
                            }
                        }
                    } else {
                        if (auto) {
                            dismissAutoDlg();
                        } else {
                            dismissLoading()
                        }

                    }
                }
            }
        )
    }

    private fun setTime() {
        showLoading()
        commandQueue.customCommand(CmdTimeSet(), object : Callback() {
            override fun run() {
                aapsLogger.error(LTag.EQUILBLE, "CmdTimeSet result====" + result.success)
                if (result.success) {
                    SystemClock.sleep(50)
                    dismissLoading();
                    var time = System.currentTimeMillis();
                    val equilHistoryRecord = EquilHistoryRecord(
                        time,
                        null,
                        null,
                        EquilHistoryRecord.EventType.SET_TIME,
                        time,
                        equilPumpPlugin.serialNumber()
                    )
                    equilPumpPlugin.equilHistoryRecordDao.insert(equilHistoryRecord)
                    setModel()
                } else {
                    dismissLoading();
                    equilPumpPlugin.showToast(rh.gs(R.string.equil_error))
                }
            }
        })
    }

    private fun readFM() {
        commandQueue.customCommand(CmdDevicesGet(), object : Callback() {
            override fun run() {
                aapsLogger.error(LTag.EQUILBLE, "CmdGetDevices result====" + result.success)
                if (result.success) {
                    equilPumpPlugin.equilManager.closeBle()
                    SystemClock.sleep(50)
                    dismissLoading();
                    equilPumpPlugin.equilManager.readStatus()
                    runOnUiThread {
                        // binding.navButtonsLayout.buttonNext.performClick()
                        val nextPage = getNextPageActionId()
                        if (nextPage != null) {
                            findNavController().navigate(nextPage)
                        }
                    }
                } else {
                    dismissLoading();
                    equilPumpPlugin.showToast(rh.gs(R.string.equil_error))
                }
            }
        })
    }

    private fun setProfile() {
        var profile = profileFunction.getProfile();
        if (profile == null) {
            setTime()
            return
        }
        commandQueue.customCommand(CmdBasalSet(profile), object : Callback() {
            override fun run() {
                aapsLogger.error(LTag.EQUILBLE, "CmdTimeSet result====" + result.success)
                if (result.success) {
                    SystemClock.sleep(50)
                    dismissLoading()
                    equilPumpPlugin.addBasalProfileLog()
                    setTime()
                } else {
                    dismissLoading();
                    equilPumpPlugin.showToast(rh.gs(R.string.equil_error))
                }

            }
        })
    }

    private fun setModel() {
        showLoading()
        commandQueue.customCommand(CmdModelSet(RunMode.RUN.command), object : Callback() {
            override fun run() {
                aapsLogger.error(LTag.EQUILBLE, "setModel result====" + result.success + "====")
                if (result.success) {
                    dismissLoading();
                    equilPumpPlugin.equilManager.runMode = RunMode.RUN;
                    SystemClock.sleep(50)
                    readFM();
                } else {
                    dismissLoading();
                }
            }
        })
    }
}
