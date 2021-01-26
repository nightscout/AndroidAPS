package info.nightscout.androidaps.activities

import android.os.Bundle
import info.nightscout.androidaps.R
import info.nightscout.androidaps.databinding.ActivityStatsBinding
import info.nightscout.androidaps.utils.ActivityMonitor
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.stats.TddCalculator
import info.nightscout.androidaps.utils.stats.TirCalculator
import javax.inject.Inject

class StatsActivity : NoSplashAppCompatActivity() {

    @Inject lateinit var tddCalculator: TddCalculator
    @Inject lateinit var tirCalculator: TirCalculator
    @Inject lateinit var activityMonitor: ActivityMonitor

    private lateinit var binding: ActivityStatsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStatsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tdds.text = tddCalculator.stats()
        binding.tir.text = tirCalculator.stats()
        binding.activity.text = activityMonitor.stats()

        binding.ok.setOnClickListener { finish() }
        binding.reset.setOnClickListener {
            OKDialog.showConfirmation(this, resourceHelper.gs(R.string.doyouwantresetstats)) {
                activityMonitor.reset()
                recreate()
            }
        }
    }
}
