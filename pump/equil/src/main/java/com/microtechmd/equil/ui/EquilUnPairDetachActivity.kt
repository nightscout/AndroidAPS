package com.microtechmd.equil.ui

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.MenuItem
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.BlePreCheck
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import com.bumptech.glide.Glide
import com.microtechmd.equil.EquilPumpPlugin
import com.microtechmd.equil.R
import com.microtechmd.equil.data.RunMode
import com.microtechmd.equil.databinding.EquilUnpairDetachActivityBinding
import com.microtechmd.equil.driver.definition.ActivationProgress
import com.microtechmd.equil.events.EventEquilUnPairChanged
import com.microtechmd.equil.manager.command.CmdInsulinChange
import com.microtechmd.equil.ui.dlg.LoadingDlg
import dagger.android.support.DaggerAppCompatActivity
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject

// IMPORTANT: This activity needs to be called from RileyLinkSelectPreference (see pref_medtronic.xml as example)
class EquilUnPairDetachActivity : DaggerAppCompatActivity() {

    @Inject lateinit var sp: SP
    @Inject lateinit var blePreCheck: BlePreCheck
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var context: Context
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var equilPumpPlugin: EquilPumpPlugin
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    private val disposable = CompositeDisposable()

    private lateinit var binding: EquilUnpairDetachActivityBinding
    @Inject lateinit var profileFunction: ProfileFunction
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = EquilUnpairDetachActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = rh.gs(R.string.equil_change)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        disposable += rxBus
            .toObservable(EventEquilUnPairChanged::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ finish() }, fabricPrivacy::logException)
        Glide.with(this)
            .asGif()
            .load(R.drawable.equil_animation_wizard_detach)
            .into(binding.imv)
        binding.btnNext.setOnClickListener {
            // startActivity(Intent(context, EquilPairInsertActivity::class.java))
            showUnPairConfig();
        }
    }

    private fun showUnPairConfig() {

        val alertDialog = AlertDialog.Builder(this)
            .setTitle(rh.gs(R.string.equil_title_tips))
            .setMessage(rh.gs(R.string.equil_hint_dressing))
            .setPositiveButton(rh.gs(app.aaps.core.ui.R.string.ok)) { dialog: DialogInterface, _: Int ->
                changeInsulin()
            }
            .setNegativeButton(rh.gs(app.aaps.core.ui.R.string.cancel)) { dialog: DialogInterface, _: Int ->
                dialog.dismiss()
            }
            .create()
        alertDialog.show()

    }

    fun changeInsulin() {
        showLoading();
        commandQueue.customCommand(CmdInsulinChange(), object : Callback() {
            override fun run() {
                if (result.success) {
                    equilPumpPlugin.equilManager.runMode = RunMode.STOP
                    equilPumpPlugin.equilManager.closeBle();
                    equilPumpPlugin.resetData()
                    SystemClock.sleep(500)
                    equilPumpPlugin.equilManager.activationProgress=ActivationProgress.CANNULA_CHANGE
                    dismissLoading()
                    startActivity(Intent(context, EquilUnPairActivity::class.java))
                } else {
                    equilPumpPlugin.showToast(rh.gs(R.string.equil_error))
                    dismissLoading()

                }
            }
        })
    }

    private fun showLoading() {
        LoadingDlg().also { dialog ->
        }.show(supportFragmentManager, "loading")
    }

    private fun dismissLoading() {
        val fragment = supportFragmentManager.findFragmentByTag("loading")
        if (fragment is DialogFragment) {
            fragment.dismiss()
        }
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

}
