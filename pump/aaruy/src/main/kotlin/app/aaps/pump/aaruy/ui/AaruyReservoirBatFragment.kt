package app.aaps.pump.aaruy.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.RadioButton
import androidx.core.view.get
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import app.aaps.pump.aaruy.R
import app.aaps.pump.aaruy.code.ReplaceStep
import app.aaps.pump.aaruy.databinding.FragmentReservoirBatBinding
import app.aaps.pump.aaruy.ui.dialog.AaruyAlertDialog
import app.aaps.pump.aaruy.ui.viewmodel.AaruyViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AaruyReservoirBatFragment : AaruyBaseFragment<FragmentReservoirBatBinding>() {

    override fun getLayoutId(): Int = R.layout.fragment_reservoir_bat
    @Volatile
    var isPushLocating: Boolean = false
    private var TAG: String = ""
    private var alertDialog: AaruyAlertDialog? = null

    companion object {

        fun newInstance(): AaruyReservoirBatFragment = AaruyReservoirBatFragment()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initData()
        bindingEventDeal()
    }

    private fun initData() {
        binding.viewModel?.apply {
            val radiobutton = binding.rgReservoir[aaruyPump.softNeedleType] as RadioButton
            radiobutton.isChecked = true
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun bindingEventDeal() {
        binding.apply {
            viewModel = ViewModelProvider(requireActivity(), viewModelFactory)[AaruyViewModel::class.java]
            viewModel?.apply {
                canSendCmd.observe(viewLifecycleOwner) {
                    if(it) {
                        btnNext.isEnabled = true
                        btnPushPosition.isEnabled = true
                        btnPushNext.isEnabled = true
                        btnPushAir.isEnabled = true
                    }
                    else {
                        btnNext.isEnabled = false
                        btnPushPosition.isEnabled = false
                        btnPushNext.isEnabled = false
                        btnPushAir.isEnabled = false
                        isPushLocating = false
                    }
                }
            }

            btnNext.setOnClickListener {
                viewModel?.apply {
                    if (replaceStep.value == ReplaceStep.REPLACE_STEP_1) {
                        replaceStep.postValue(ReplaceStep.REPLACE_STEP_2)
                    }
                    else if (replaceStep.value == ReplaceStep.REPLACE_STEP_3) {
                        replaceStep.postValue(ReplaceStep.REPLACE_STEP_4)
                    }
                }
            }

            btnPushNext.setOnClickListener { v ->
                alertDialog = AaruyAlertDialog(
                    activity as AaruyActivity,
                    getString(R.string.notifyTitle),
                    getString(R.string.actions_reservoir_prime_confirm),
                    true,
                    1
                ) { _, isPositive ->
                    if (isPositive) {
                        viewModel?.apply {
                            replaceStep.postValue(ReplaceStep.REPLACE_STEP_3)
                        }
                    }
                }
                alertDialog?.show()
            }

            btnPushPosition.setOnClickListener {
                val parent = activity as AaruyActivity
                parent.startPush()
                binding.btnPushNext.isEnabled = true
            }

            btnPushPosition.setOnLongClickListener {
                val parent = activity as AaruyActivity
                parent.startPush()
                isPushLocating = true
                binding.btnPushNext.isEnabled = true
                true
            }

            btnPushPosition.setOnTouchListener { v, event ->
                val action = event!!.action
                if (action == MotionEvent.ACTION_DOWN) {
                    Log.e(TAG, "btnPushPosition setOnTouchListener startPush")

                } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    Log.e(TAG, "btnPushPosition setOnTouchListener stopPush")
                    isPushLocating = false
                }

                false //return true没有按住的背景效果
            }

            rgReservoir.setOnCheckedChangeListener {group, checkedId ->
                when (checkedId) {
                    R.id.rb_reservoir_one -> {
                        viewModel?.apply {
                            sendNeedleType(0)
                        }
                    }
                    R.id.rb_reservoir_two -> {
                        viewModel?.apply {
                            sendNeedleType(1)
                        }
                    }
                }
            }

            btnPushAir.setOnClickListener {
                lifecycleScope.launch {
                    delay(100)
                    val parent = activity as AaruyActivity
                    parent.startRemoveAir()
                }
            }

            btnStartBase.setOnClickListener {
                viewModel?.apply {
                    startBaseInject()
                    // val parent = activity as AaruyActivity
                    // parent.finish()
                }
            }
        }
    }

    fun notifyTextView(step: ReplaceStep) {
        lifecycleScope.launch {
            if (!isAdded) {
                return@launch
            }
            if (step == ReplaceStep.REPLACE_STEP_1) {
                binding.tvReservoirTip.text =
                    getString(R.string.actions_reservoir_prime_before)//  actions_reservoir_prime_comment
            } else if (step == ReplaceStep.REPLACE_STEP_2) {
                binding.tvReservoirTip.text = getString(R.string.actions_reservoir_prime_comment)
                binding.tvReservoirNote.visibility = View.VISIBLE
                binding.layoutButtonNext.visibility = View.GONE
                binding.llPushPosition.visibility = View.VISIBLE
                binding.btnPushNext.isEnabled = false
                binding.tvReservoirNote.text =
                    getString(R.string.actions_reservoir_prime_comment_note)
            } else if (step == ReplaceStep.REPLACE_STEP_3) {
                binding.tvReservoirNote.visibility = View.GONE
                binding.layoutButtonNext.visibility = View.VISIBLE
                binding.llPushPosition.visibility = View.GONE
                binding.tvReservoirTip.text = getString(R.string.actions_reservoir_prime_cannula_before)
            } else if (step ==  ReplaceStep.REPLACE_STEP_4) {
                binding.tvReservoirTip.text = getString(R.string.actions_reservoir_prime_cannula)
                binding.rgReservoir.visibility = View.VISIBLE
                binding.llPrimeSkip.visibility = View.VISIBLE
                binding.layoutButtonNext.visibility = View.GONE
                binding.llPushPosition.visibility = View.GONE
                binding.tvReservoirNote.visibility = View.VISIBLE
                binding.tvReservoirNote.text = getString(R.string.actions_reservoir_prime_cannula_note)

                initData()
            }
        }

    }

    fun setNextBtnEnable(isEnable: Boolean) {
        binding.btnNext.isEnabled = isEnable
        binding.btnPushNext.isEnabled = isEnable
    }
}