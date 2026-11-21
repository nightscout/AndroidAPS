package app.aaps.pump.aaruy.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.aaruy.emulator.AaruyEmulatorPeripheral
import dev.zacsweers.metro.Inject

/** The adb broadcasts that drive the emulated pump, for tests on an emulator or a phone. */
@Inject
class AndroidAaruyEmulatorTriggers(
    private val context: Context,
    private val aapsLogger: AAPSLogger
) : AaruyEmulatorTriggers {

    override fun start(emulator: AaruyEmulatorPeripheral) {
        // For tests: adb shell am broadcast -a app.aaps.pump.aaruy.emulator.OUT_OF_RANGE --ez value true
        ContextCompat.registerReceiver(context, object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val outOfRange = intent.getBooleanExtra("value", true)
                aapsLogger.warn(LTag.PUMPEMULATOR, "Aaruy emulator: out of range = $outOfRange")
                emulator.setOutOfRange(outOfRange)
            }
        }, IntentFilter("app.aaps.pump.aaruy.emulator.OUT_OF_RANGE"), ContextCompat.RECEIVER_EXPORTED)
        // For tests: adb shell am broadcast -a app.aaps.pump.aaruy.emulator.SCENARIO --es value temp_basal_start --ei rate 500 --ei minutes 30
        ContextCompat.registerReceiver(context, object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val scenario = intent.getStringExtra("value")
                aapsLogger.warn(LTag.PUMPEMULATOR, "Aaruy emulator: scenario $scenario")
                emulator.onPumpThread {
                    val pump = emulator.emulator
                    val messages = when (scenario) {
                        "lose_answer_temp_basal" -> emptyList<ByteArray>().also { emulator.loseAnswerToParam = TEMP_BASAL_PARAM }
                        "lose_answer_bolus"      -> emptyList<ByteArray>().also { emulator.loseAnswerToParam = BOLUS_CONTROL_PARAM }
                        "temp_basal_start"       -> listOf(pump.startTempBasalByItself(intent.getIntExtra("rate", 500), intent.getIntExtra("minutes", 30)))
                        "temp_basal_end"         -> listOf(pump.endTempBasalByItself())
                        "stop"                   -> listOf(pump.stopByItself())
                        "resume"                 -> listOf(pump.resumeByItself())
                        "occlusion"              -> pump.blockMotor()
                        else                     -> emptyList()
                    }
                    messages.forEach { emulator.pumpSends(it) }
                }
            }
        }, IntentFilter("app.aaps.pump.aaruy.emulator.SCENARIO"), ContextCompat.RECEIVER_EXPORTED)
    }

    private companion object {

        const val TEMP_BASAL_PARAM = 8
        const val BOLUS_CONTROL_PARAM = 9
    }
}
