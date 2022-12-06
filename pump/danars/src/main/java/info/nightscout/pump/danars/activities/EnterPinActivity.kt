package info.nightscout.pump.danars.activities

import android.os.Bundle
import android.util.Base64
import dagger.android.support.DaggerAppCompatActivity
import info.nightscout.core.ui.dialogs.OKDialog
import info.nightscout.core.utils.fabric.FabricPrivacy
import info.nightscout.core.utils.hexStringToByteArray
import info.nightscout.core.validators.DefaultEditTextValidator
import info.nightscout.core.validators.EditTextValidator
import info.nightscout.pump.danars.DanaRSPlugin
import info.nightscout.pump.danars.databinding.DanarsEnterPinActivityBinding
import info.nightscout.pump.danars.services.BLEComm
import info.nightscout.rx.AapsSchedulers
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventPumpStatusChanged
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject
import kotlin.experimental.xor

class EnterPinActivity : DaggerAppCompatActivity() {

    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var danaRSPlugin: DanaRSPlugin
    @Inject lateinit var sp: SP
    @Inject lateinit var bleComm: BLEComm
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var rxBus: RxBus

    private val disposable = CompositeDisposable()

    private lateinit var binding: DanarsEnterPinActivityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DanarsEnterPinActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val p1 = DefaultEditTextValidator(binding.rsV3Pin1, this)
            .setTestErrorString(rh.gs(info.nightscout.core.validators.R.string.error_mustbe12hexadidits), this)
            .setCustomRegexp(rh.gs(info.nightscout.core.validators.R.string.twelvehexanumber), this)
            .setTestType(EditTextValidator.TEST_REGEXP, this)
        val p2 = DefaultEditTextValidator(binding.rsV3Pin2, this)
            .setTestErrorString(rh.gs(info.nightscout.core.validators.R.string.error_mustbe8hexadidits), this)
            .setCustomRegexp(rh.gs(info.nightscout.core.validators.R.string.eighthexanumber), this)
            .setTestType(EditTextValidator.TEST_REGEXP, this)

        binding.okcancel.ok.setOnClickListener {
            if (p1.testValidity(false) && p2.testValidity(false)) {
                val result = checkPairingCheckSum(
                    binding.rsV3Pin1.text.toString().hexStringToByteArray(),
                    binding.rsV3Pin2.text.toString().substring(0..5).hexStringToByteArray(),
                    binding.rsV3Pin2.text.toString().substring(6..7).hexStringToByteArray())
                if (result) {
                    bleComm.finishV3Pairing()
                    finish()
                } else OKDialog.show(this, rh.gs(info.nightscout.core.ui.R.string.error), rh.gs(info.nightscout.core.ui.R.string.invalid_input))
            }
        }
        binding.okcancel.cancel.setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        disposable += rxBus
            .toObservable(EventPumpStatusChanged::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ if (it.status == EventPumpStatusChanged.Status.DISCONNECTED) finish() }, fabricPrivacy::logException)
    }

    override fun onPause() {
        super.onPause()
        disposable.clear()
    }

    private fun checkPairingCheckSum(pairingKey: ByteArray, randomPairingKey: ByteArray, checksum: ByteArray): Boolean {

        // pairingKey ByteArray(6)
        // randomPairingKey ByteArray(3)
        // checksum ByteArray(1)

        var pairingKeyCheckSum: Byte = 0
        for (i in pairingKey.indices)
            pairingKeyCheckSum = pairingKeyCheckSum xor pairingKey[i]

        sp.putString(rh.gs(info.nightscout.pump.dana.R.string.key_danars_v3_pairingkey) + danaRSPlugin.mDeviceName, Base64.encodeToString(pairingKey, Base64.DEFAULT))

        for (i in randomPairingKey.indices)
            pairingKeyCheckSum = pairingKeyCheckSum xor randomPairingKey[i]

        sp.putString(rh.gs(info.nightscout.pump.dana.R.string.key_danars_v3_randompairingkey) + danaRSPlugin.mDeviceName, Base64.encodeToString(randomPairingKey, Base64.DEFAULT))

        return checksum[0] == pairingKeyCheckSum
    }

}
