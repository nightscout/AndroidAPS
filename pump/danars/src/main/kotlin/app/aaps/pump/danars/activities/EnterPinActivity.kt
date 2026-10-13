package app.aaps.pump.danars.activities

import android.os.Bundle
import android.util.Base64
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.activities.TranslatedDaggerAppCompatActivity
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.core.utils.hexStringToByteArray
import app.aaps.core.validators.DefaultEditTextValidator
import app.aaps.core.validators.EditTextValidator
import app.aaps.pump.dana.keys.DanaStringComposedKey
import app.aaps.pump.danars.DanaRSPlugin
import app.aaps.pump.danars.databinding.DanarsEnterPinActivityBinding
import app.aaps.pump.danars.services.BLEComm
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject
import kotlin.experimental.xor

class EnterPinActivity : TranslatedDaggerAppCompatActivity() {

    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var danaRSPlugin: DanaRSPlugin
    @Inject lateinit var preferences: Preferences
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
            .setTestErrorString(rh.gs(app.aaps.core.validators.R.string.error_mustbe12hexadidits), this)
            .setCustomRegexp(rh.gs(app.aaps.core.validators.R.string.twelvehexanumber), this)
            .setTestType(EditTextValidator.TEST_REGEXP, this)
        val p2 = DefaultEditTextValidator(binding.rsV3Pin2, this)
            .setTestErrorString(rh.gs(app.aaps.core.validators.R.string.error_mustbe8hexadidits), this)
            .setCustomRegexp(rh.gs(app.aaps.core.validators.R.string.eighthexanumber), this)
            .setTestType(EditTextValidator.TEST_REGEXP, this)

        binding.okcancel.ok.setOnClickListener {
            if (p1.testValidity(false) && p2.testValidity(false)) {
                val result = checkPairingCheckSum(
                    binding.rsV3Pin1.text.toString().hexStringToByteArray(),
                    binding.rsV3Pin2.text.toString().substring(0..5).hexStringToByteArray(),
                    binding.rsV3Pin2.text.toString().substring(6..7).hexStringToByteArray()
                )
                if (result) {
                    bleComm.finishV3Pairing()
                    finish()
                } else OKDialog.show(this, rh.gs(app.aaps.core.ui.R.string.error), rh.gs(app.aaps.core.ui.R.string.invalid_input))
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

    override fun onDestroy() {
        super.onDestroy()
        binding.okcancel.ok.setOnClickListener(null)
        binding.okcancel.cancel.setOnClickListener(null)
    }

    private fun checkPairingCheckSum(pairingKey: ByteArray, randomPairingKey: ByteArray, checksum: ByteArray): Boolean {

        // pairingKey ByteArray(6)
        // randomPairingKey ByteArray(3)
        // checksum ByteArray(1)

        var pairingKeyCheckSum: Byte = 0
        for (i in pairingKey.indices)
            pairingKeyCheckSum = pairingKeyCheckSum xor pairingKey[i]

        preferences.put(DanaStringComposedKey.V3ParingKey, danaRSPlugin.mDeviceName, value = Base64.encodeToString(pairingKey, Base64.DEFAULT))

        for (i in randomPairingKey.indices)
            pairingKeyCheckSum = pairingKeyCheckSum xor randomPairingKey[i]

        preferences.put(DanaStringComposedKey.V3RandomParingKey, danaRSPlugin.mDeviceName, value = Base64.encodeToString(randomPairingKey, Base64.DEFAULT))

        return checksum[0] == pairingKeyCheckSum
    }

}
