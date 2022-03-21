package info.nightscout.androidaps.activities

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.TextView
import info.nightscout.androidaps.R
import info.nightscout.androidaps.database.entities.UserEntry.Action
import info.nightscout.androidaps.database.entities.UserEntry.Sources
import info.nightscout.androidaps.databinding.ActivityStatsBinding
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.utils.ActivityMonitor
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.stats.TddCalculator
import info.nightscout.androidaps.utils.stats.TirCalculator
import javax.inject.Inject

class StatsActivity : NoSplashAppCompatActivity() {

    @Inject lateinit var tddCalculator: TddCalculator
    @Inject lateinit var tirCalculator: TirCalculator
    @Inject lateinit var activityMonitor: ActivityMonitor
    @Inject lateinit var uel: UserEntryLogger

    private lateinit var binding: ActivityStatsBinding

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStatsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tdds.addView(TextView(this).apply { text = getString(R.string.tdd) + ": " + rh.gs(R.string.calculation_in_progress) })
        binding.tir.addView(TextView(this).apply { text = getString(R.string.tir) + ": " + rh.gs(R.string.calculation_in_progress) })
        binding.activity.addView(TextView(this).apply { text = getString(R.string.activitymonitor) + ": " + rh.gs(R.string.calculation_in_progress) })

        Thread {
            val tdds = tddCalculator.stats(this)
            runOnUiThread {
                binding.tdds.removeAllViews()
                binding.tdds.addView(tdds)
            }
        }.start()
        Thread {
            val tir = tirCalculator.stats(this)
            runOnUiThread {
                binding.tir.removeAllViews()
                binding.tir.addView(tir)
            }
        }.start()
        Thread {
            val activity = activityMonitor.stats(this)
            runOnUiThread {
                binding.activity.removeAllViews()
                binding.activity.addView(activity)
            }
        }.start()

        binding.ok.setOnClickListener { finish() }
        binding.reset.setOnClickListener {
            OKDialog.showConfirmation(this, rh.gs(R.string.doyouwantresetstats)) {
                uel.log(Action.STAT_RESET, Sources.Stats)
                activityMonitor.reset()
                recreate()
            }
        }
    }
}
