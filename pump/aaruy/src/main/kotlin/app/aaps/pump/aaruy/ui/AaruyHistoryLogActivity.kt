package app.aaps.pump.aaruy.ui

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.pump.aaruy.R
import app.aaps.pump.aaruy.code.ConnectionState
import app.aaps.pump.aaruy.common.util.AaruyNumberUtils
import app.aaps.pump.aaruy.common.util.AaruyTimeUtil
import app.aaps.pump.aaruy.database.HistoryDailyUnitInfo
import app.aaps.pump.aaruy.database.HistoryUnitInfo
import app.aaps.pump.aaruy.databinding.ActivityAaruyHistoryLogBinding
import app.aaps.pump.aaruy.ui.dialog.CustomDialogFragment
import app.aaps.pump.aaruy.ui.history.DailyItem
import app.aaps.pump.aaruy.ui.history.DailyTotalAdapter
import app.aaps.pump.aaruy.ui.history.HistoryLogDateData
import app.aaps.pump.aaruy.ui.history.HistoryLogRecyclerviewAdapter
import app.aaps.pump.aaruy.ui.history.SpacesItemDecoration
import app.aaps.pump.aaruy.ui.viewmodel.AaruyHistoryViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AaruyHistoryLogActivity : AaruyBaseActivity<ActivityAaruyHistoryLogBinding>() {

    private var carbsShow: Boolean = true
    private var alarmShow: Boolean = true
    private var insulinShow: Boolean = true
    private var bgShow: Boolean = true
    private var historyTotalRecordList: MutableList<HistoryUnitInfo> = mutableListOf()

    private var mAdapter: HistoryLogRecyclerviewAdapter? = null
    private var year: Int = 2021
    private var month: Int = 3
    private var day: Int = 20
    private var week: Array<String>? = null
    private var dateWeekString: String = ""

    private var currentDayHistoryLog: MutableList<HistoryUnitInfo>? = ArrayList()
    private var currentDayLastIndex: Int = -1//当前日期列表的最后一个下标值

    private var newestHistoryTime: Long = 0//历史记录最近的时间，单位秒
    private var totalLogNumber: Long = 0
    private var dailyItems: MutableList<DailyItem> = arrayListOf()
    private var detailTime: IntArray? = intArrayOf()
    private var adapter: DailyTotalAdapter? = null
    private var weekDay: String? = null
    private var timeStamp: Long? = 0 //某条历史记录所在当天0点的时间戳，单位秒
    private var dayInternalTime: Long = 86400//单位秒，一天多少时间

    private var lastHistoryDailyTime: Long = 0
    private var historyDailyItems: MutableList<HistoryDailyUnitInfo> = mutableListOf()

    private val selectHistoryLog: MutableList<HistoryUnitInfo> = mutableListOf()

    private var historyLogDateDataList: MutableList<HistoryLogDateData> = mutableListOf()
    private var totalHistoryMapList: Map<String, List<HistoryLogDateData>> = mapOf()
    private var totalDateList: List<String> = listOf()
    private var currentDateIndex = 0

    private var daily_total_view: LinearLayout? = null

    override fun getLayoutId(): Int = R.layout.activity_aaruy_history_log

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activity = this@AaruyHistoryLogActivity
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        title = getString(R.string.history_log_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        createObserve()
    }

    override fun onDestroy() {
        super.onDestroy()
        activity = null
    }

    private fun createObserve() {
        binding.apply {
            viewModel = ViewModelProvider(this@AaruyHistoryLogActivity, viewModelFactory)[AaruyHistoryViewModel::class.java]
        }

        initView()
        initEvent()
        initData()
    }

    private fun initView() {
        val btnCancel: Button = findViewById(R.id.btn_cancel)
        val rcvDaily: RecyclerView = findViewById(R.id.rcv_daily_total)
        daily_total_view = findViewById(R.id.daily_total_view)

        binding.btnDailyTotal.setOnClickListener {
            daily_total_view!!.visibility = View.VISIBLE
            binding.btnDailyTotal.visibility = View.GONE
        }
        btnCancel.setOnClickListener {
            daily_total_view!!.visibility = View.GONE
            binding.btnDailyTotal.visibility = View.VISIBLE
        }

        rcvDaily.layoutManager = LinearLayoutManager(this@AaruyHistoryLogActivity)
        // rcvDaily.addItemDecoration(SpacesItemDecoration(12))
        adapter = DailyTotalAdapter(activity!!, dailyItems)
        rcvDaily.adapter = adapter

        binding.historyLogRc.layoutManager = LinearLayoutManager(this)
        // binding.historyLogRc.addItemDecoration(SpacesItemDecoration(2))
    }

    private fun initEvent() {
        binding.cbPortraitCarbs.setOnCheckedChangeListener { _, isChecked ->
            carbsShow = isChecked
            setAdapterData()
        }

        binding.cbPortraitAlarm.setOnCheckedChangeListener { _, isChecked ->
            alarmShow = isChecked
            setAdapterData()
        }

        binding.cbPortraitInsulin.setOnCheckedChangeListener { _, isChecked ->
            insulinShow = isChecked
            setAdapterData()
        }

        binding.cbPortraitBg.setOnCheckedChangeListener { _, isChecked ->
            bgShow = isChecked
            setAdapterData()
        }

        binding.ivLeft.setOnClickListener {

            if (totalDateList.isEmpty()) {
                return@setOnClickListener
            }

            if (currentDateIndex < totalLogNumber - 1) {
                currentDateIndex += 1
                val time = totalHistoryMapList.get(totalDateList[currentDateIndex])!!.get(0).historyUnitInfo.time

                refreshDate(time)
                getSelectHistoryLog(
                    timeStamp!!,
                    false
                )
            } else {
                if (currentDateIndex == -1) {
                    currentDateIndex = 0
                    val time = totalHistoryMapList.get(totalDateList[currentDateIndex])!!.get(0).historyUnitInfo.time

                    refreshDate(time)
                    getSelectHistoryLog(
                        timeStamp!!,
                        false
                    )
                }
            }
        }

        binding.ivRight.setOnClickListener {

            if (totalDateList.isEmpty()) {
                return@setOnClickListener
            }

            if (currentDateIndex > 0) {
                currentDateIndex -= 1
                val time = totalHistoryMapList.get(totalDateList[currentDateIndex])!!.get(0).historyUnitInfo.time

                refreshDate(time)
                getSelectHistoryLog(
                    timeStamp!!,
                    false
                )
            } else {
                if (currentDateIndex == -1) {
                    currentDateIndex = totalDateList.size - 1
                    val time = totalHistoryMapList.get(totalDateList[currentDateIndex])!!.get(0).historyUnitInfo.time

                    refreshDate(time)
                    getSelectHistoryLog(
                        timeStamp!!,
                        false
                    )
                }

            }
        }

        binding.tvDate.setOnClickListener {
            showDateDialog()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun initData() {

        week = arrayOf(
            getString(R.string.sunday), getString(R.string.monday),
            getString(R.string.tuesday), getString(R.string.wednesday),
            getString(R.string.thursday), getString(R.string.friday),
            getString(R.string.saturday)
        )


        mAdapter = HistoryLogRecyclerviewAdapter(this@AaruyHistoryLogActivity, currentDayHistoryLog)
        binding.historyLogRc.adapter = mAdapter

        binding.viewModel?.getAllHistoryLiveData()?.observe(this@AaruyHistoryLogActivity, Observer {
            aapsLogger.info(LTag.PUMP, "historyLog size=${it.size}")

            historyTotalRecordList.clear()
            if (it.isNotEmpty()) {

                var lastIsAbnormal = false
                for (i in it.indices) {
                    val item = it[i]

                    var nextItem: HistoryUnitInfo? = null
                    if (i + 1 < it.size) {
                        nextItem = it[i + 1]
                    }
                    //当前是大剂量或临时基础率时，判断下次记录是否是报警，如果是，则判断当前的起始时间是否覆盖报警的时间，是的话,isAbnormal = true，就把报警记录添加到列表
                    var isAbnormal = false
                    if (lastIsAbnormal) {
                        lastIsAbnormal = false
                    } else {
                        if (nextItem != null && nextItem.type == 3) {
                            if (item.type == 2 || item.type == 1) {
                                aapsLogger.info(LTag.PUMP, "item = $item,nextItem = $item")
                                val nextTime = nextItem.time
                                val startTime = item.time
                                var endTime = 0L
                                if (item.type == 2) {
                                    if (item.injectTime.toInt() == 0) {
                                        endTime = startTime + (item.data / 25) * 2
                                    } else {
                                        endTime = startTime + item.injectTime * 60
                                    }
                                } else {
                                    endTime = startTime + item.injectTime * 60
                                }

                                if (getDateStr(startTime) != getDateStr(endTime)) {
                                    if (nextTime in (startTime + 1) until endTime) {
                                        isAbnormal = true
                                    }
                                }
                            }
                        }
                    }
                    val historyLogDateData = HistoryLogDateData(getDateStr(item.time), item)
                    lastIsAbnormal = isAbnormal
                    if (isAbnormal) {
                        val nextHistoryLogDateData =
                            HistoryLogDateData(getDateStr(nextItem!!.time), nextItem)
                        historyLogDateDataList.add(nextHistoryLogDateData)
                        historyLogDateDataList.add(historyLogDateData)

                    } else {
                        historyLogDateDataList.add(historyLogDateData)
                    }
                }
                totalHistoryMapList = historyLogDateDataList.groupBy { it.date }
                if (totalHistoryMapList.isNotEmpty()) {
                    totalLogNumber = totalHistoryMapList.size.toLong()
                    totalDateList = totalHistoryMapList.keys.toList()
                    newestHistoryTime =
                        totalHistoryMapList.get(totalDateList[currentDateIndex])!!
                            .get(0).historyUnitInfo.time
                    val time = newestHistoryTime

                    refreshDate(time)
                    CoroutineScope(Dispatchers.Default).launch {
                        delay(100)
                        getSelectHistoryLog(timeStamp!!, true)
                    }
                }

            } else {

                val time = System.currentTimeMillis()
                refreshDate(time)
            }

        })

        binding.viewModel?.getAllHistoryDailyLiveData()?.observe(this@AaruyHistoryLogActivity, Observer {
            aapsLogger.info(LTag.PUMP, "historyDaily size = ${it.size}")
            if (it.isEmpty()) {
                return@Observer
            }
            historyDailyItems = it
            showInjectInfo(timeStamp!!)
            lastHistoryDailyTime = it[0].dateTimestamp
            aapsLogger.info(LTag.PUMP, "historyDaily lastHistoryDailyTime = $lastHistoryDailyTime")
            dailyItems.clear()
            var count: Int = it!!.size
            if (count > 30) {
                count = 30
            }
            var lastItem: HistoryDailyUnitInfo? = null
            var finalItem: HistoryDailyUnitInfo? = null
            for (i in 0 until count) {
                val item = it[i]
                if (i > 0) {
                    lastItem = it[i - 1]
                    if (item.pumpName != lastItem.pumpName){
                        val dailyItem = DailyItem()

                        dailyItem.isReplace = true
                        dailyItem.time = lastItem.dateTimestamp
                        dailyItem.pumpName = lastItem.pumpName

                        dailyItems.add(dailyItem)
                    }else{
                        if (item.dateTimestamp != lastItem.dateTimestamp){
                            val dailyItem = DailyItem()
                            dailyItem.total = AaruyNumberUtils.getDotOneDouble(item.dailyTotal.toDouble() / 10)
                            dailyItem.time = item.dateTimestamp
                            dailyItem.pumpName = item.pumpName
                            dailyItem.isReplace = false

                            dailyItems.add(dailyItem)
                        }
                    }
                }else{
                    val dailyItem = DailyItem()
                    dailyItem.total = AaruyNumberUtils.getDotOneDouble(item.dailyTotal.toDouble() / 10)
                    dailyItem.time = item.dateTimestamp
                    dailyItem.pumpName = item.pumpName
                    dailyItem.isReplace = false

                    dailyItems.add(dailyItem)
                }
                //aapsLogger.info(LTag.PUMP, "daily item = ${item.toString()}")
                if (i == count - 1){
                    finalItem = item
                }

            }

            if (finalItem != null){
                val dailyItem = DailyItem()

                dailyItem.isReplace = true
                dailyItem.time = finalItem.dateTimestamp
                dailyItem.pumpName = finalItem.pumpName

                dailyItems.add(dailyItem)
            }


            if (dailyItems.isNotEmpty()) {
                if (adapter != null) {
                    adapter!!.notifyDataSetChanged()
                }
            }
        })

        CoroutineScope(Dispatchers.Default).launch {
            delay(500)
            binding.viewModel?.apply {
                if (aaruyPump.connectionState == ConnectionState.CONNECTED) {
                    // readDailyData("")
                    requestHistoryData()
                }
                else {
                    connectDevice()
                }
            }
        }
    }

    private fun setAdapterData() {
        selectHistoryLog.clear()
        for (i in currentDayHistoryLog!!.size - 1 downTo 0) {
            val item = currentDayHistoryLog!![i]
            if (item.type == 2) {
                if (insulinShow) {
                    addSelectHistoryLog(item)
                }

            } else if (item.type == 0 || item.type == 1) {
                if (insulinShow) {
                    addSelectHistoryLog(item)
                }
            } else if (item.type == 3) {
                if (alarmShow) {
                    addSelectHistoryLog(item)
                }
            } else if (item.type == 108) {
                if (carbsShow) {
                    addSelectHistoryLog(item)
                }
            } else if (item.type == 109) {
                if (bgShow) {
                    addSelectHistoryLog(item)
                }
            } else {
                addSelectHistoryLog(item)
            }
        }
        CoroutineScope(Dispatchers.Main).launch {
            if (mAdapter != null) {
                runOnUiThread {
                    mAdapter!!.setData(selectHistoryLog)
                }
            }
        }
    }

    private fun addSelectHistoryLog(item: HistoryUnitInfo) {
        var isExist = false
        for (log in selectHistoryLog) {
            isExist = log.time == item.time && log.type == item.type
            if (isExist) {
                break
            }
        }

        if (!isExist) {
            selectHistoryLog.add(0, item)
        }
    }

    private fun getSelectHistoryLog(
        timeStamp: Long,
        selectDate: Boolean
    ) {
        CoroutineScope(Dispatchers.Default).launch {
            val historyRecordList: MutableList<HistoryUnitInfo> = mutableListOf()
            var totalCarbsValue = 0.0
            if (currentDateIndex < 0 || currentDateIndex > totalDateList.size - 1) {
                setAdapterData()
                return@launch
            }
            val currentHistoryLogDateDataList =
                totalHistoryMapList.get(totalDateList[currentDateIndex])
            if (currentHistoryLogDateDataList.isNullOrEmpty()) {
                setAdapterData()
                return@launch
            }
            for (logDateData in currentHistoryLogDateDataList) {
                val item = logDateData.historyUnitInfo
                historyRecordList.add(item)
                if (item.type == 108) {
                    totalCarbsValue += item.delayBolus
                }
            }

            showInjectInfo(timeStamp)
            currentDayHistoryLog = historyRecordList

            if (historyRecordList.size > 0) {
                runOnUiThread {
                    binding.historyCarbsValueTv.text =
                        AaruyNumberUtils.getDotTwoString(totalCarbsValue) + getString(R.string.history_carbs_unit)
                }
                setAdapterData()
            } else {
                if (selectDate) {
                    runOnUiThread {
                        binding.historyCarbsValueTv.text =
                            AaruyNumberUtils.getDotTwoString(totalCarbsValue) + getString(R.string.history_carbs_unit)
                    }
                    if (mAdapter != null) {
                        selectHistoryLog.clear()
                        selectHistoryLog.addAll(historyRecordList)
                        runOnUiThread {
                            mAdapter!!.setData(historyRecordList)
                        }
                    }
                }

            }
        }
    }

    private fun getDateStr(time: Long): String {
        val factTime = time
        val detailTime = AaruyTimeUtil.getWholeDetail(factTime)
        val weekDay = AaruyTimeUtil.getWeekOfDate(factTime, week)

        val timeStamp =
            (AaruyTimeUtil.dateToStamp("${detailTime!![0]}-${detailTime!![1]}-${detailTime!![2]} 00:00:00"))

        val dateStr = "${detailTime!![0]}-${
            String.format(
                getString(R.string.time_two_num_format),
                detailTime!![1]
            )
        }-${
            String.format(
                getString(R.string.time_two_num_format),
                detailTime!![2]
            )
        }"
        aapsLogger.info(LTag.PUMP, "getAbnormalDateStr timeStamp = $timeStamp,dateStr = $dateStr")
        return dateStr
    }

    @SuppressLint("SetTextI18n")
    private fun refreshDate(time: Long) {
        detailTime = AaruyTimeUtil.getWholeDetail(time)

        timeStamp =
            (AaruyTimeUtil.dateToStamp("${detailTime!![0]}-${detailTime!![1]}-${detailTime!![2]} 00:00:00"))

        weekDay = AaruyTimeUtil.getWeekOfDate(time, week)
        aapsLogger.info(LTag.PUMP, "detailTime = ${detailTime?.toList()}, timeStamp is $timeStamp")
        lifecycleScope.launch {
            try {
                binding.tvDate.text =
                    "${detailTime!![0]}-${
                        String.format(
                            getString(R.string.time_two_num_format),
                            detailTime!![1]
                        )
                    }-${
                        String.format(
                            getString(R.string.time_two_num_format),
                            detailTime!![2]
                        )
                    } $weekDay"
            } catch (e: Exception) {
                e.printStackTrace()
            }

        }
    }

    private fun showInjectInfo(timeStamp: Long) {
        for (item in historyDailyItems) {//新增每日总量
//            aapsLogger.info(LTag.PUMP,
//                "item.dateTimestamp = ${item.dateTimestamp}:${
//                    item.dateTimestamp
//                },timeStamp = $timeStamp"
//            )
            if (item.dateTimestamp == timeStamp) {
                val totalBolusValue = AaruyNumberUtils.getDotOneDouble((item.dailyBolus.toDouble() / 10))
                val totalValue = AaruyNumberUtils.getDotOneDouble(item.dailyTotal.toDouble() / 10)
                lifecycleScope.launch {
                    binding.historyBolusValueTv.text =
                        "${(totalBolusValue / (totalValue) * 100).toInt()}%"
                    binding.historyInsulinValueTv!!.text =
                        AaruyNumberUtils.getDotTwoString(totalValue) + "U"
                }
                break
            }
        }
    }

    private fun showDateDialog() {
        val dialog = CustomDialogFragment.newInstance(R.layout.fragment_date_setting_dialog)
        dialog.setMyViewInterface {
            setDateSetDialogView(it, dialog)
        }
        dialog.showDialog(activity!!.supportFragmentManager)

    }

    private fun setTextView(textView: TextView, value: Int) {
        textView.text = value.toString()
    }

    private fun setDateSetDialogView(view: View, dialog: CustomDialogFragment) {
        val iv_add_year: ImageView = view.findViewById(R.id.iv_add_year)
        val iv_add_month: ImageView = view.findViewById(R.id.iv_add_month)
        val iv_add_day: ImageView = view.findViewById(R.id.iv_add_day)

        val iv_minus_year: ImageView = view.findViewById(R.id.iv_minus_year)
        val iv_minus_month: ImageView = view.findViewById(R.id.iv_minus_month)
        val iv_minus_day: ImageView = view.findViewById(R.id.iv_minus_day)

        val tv_year_value: TextView = view.findViewById(R.id.tv_year_value)
        val tv_month_value: TextView = view.findViewById(R.id.tv_month_value)
        val tv_day_value: TextView = view.findViewById(R.id.tv_day_value)

        if (timeStamp!! > 0) {
            val time = timeStamp!!
            detailTime = AaruyTimeUtil.getWholeDetail(time)
        }


        if (detailTime!!.isNotEmpty()) {
            year = detailTime!![0]
            month = detailTime!![1]
            day = detailTime!![2]
        }
        tv_year_value.text = year.toString()
        tv_month_value.text = month.toString()
        tv_day_value.text = day.toString()

        val btn_positive: Button = view.findViewById(R.id.btn_positive)
        val btn_negative: Button = view.findViewById(R.id.btn_negative)

        fun getMaxDaysOfMonth(year: Int, month: Int): Int {
            var maxDays = 30
            when (month) {
                1, 3, 5, 7, 8, 10, 12 -> {
                    maxDays = 31
                }

                2 -> {
                    if (AaruyTimeUtil.isLeapYear(year)) {
                        maxDays = 29
                    } else {
                        maxDays = 28
                    }
                }

                4, 6, 9, 11 -> {
                    maxDays = 30
                }
            }
            if (day > maxDays) {
                day = maxDays
                tv_day_value.text = day.toString()
            }
            return maxDays
        }

        var maxDays = getMaxDaysOfMonth(year, month)

        iv_add_year.setOnClickListener { v ->
            year++
            maxDays = getMaxDaysOfMonth(year, month)
            setTextView(tv_year_value, year)
        }
        iv_minus_year.setOnClickListener { v ->
            year--
            maxDays = getMaxDaysOfMonth(year, month)
            setTextView(tv_year_value, year)
        }

        iv_add_month.setOnClickListener { v ->
            if (month == 12) {
                month = 1
            } else {
                month++
            }
            maxDays = getMaxDaysOfMonth(year, month)
            setTextView(tv_month_value, month)
        }
        iv_minus_month.setOnClickListener { v ->
            if (month == 1) {
                month = 12
            } else {
                month--
            }
            maxDays = getMaxDaysOfMonth(year, month)
            setTextView(tv_month_value, month)
        }

        iv_add_day.setOnClickListener { v ->
            if (day >= maxDays) {
                day = 1
            } else {
                day++
            }
            setTextView(tv_day_value, day)
        }
        iv_minus_day.setOnClickListener { v ->
            if (day == 1) {
                day = maxDays
            } else {
                day--
            }
            setTextView(tv_day_value, day)

        }


        btn_positive.setOnClickListener { v ->
            val format = String.format(getString(R.string.date_format), year, month, day)
            aapsLogger.info(LTag.PUMP, "format=$format")
            val dateToWeek = AaruyTimeUtil.dateToWeek(format, week)
            val dateString =
                String.format(getString(R.string.date_format_string), year, month, day)
            val date = String.format(getString(R.string.date_format), year, month, day)
            val oldTimeStamp = timeStamp
            val oldCurrentDateIndex = currentDateIndex

            timeStamp = (AaruyTimeUtil.dateToStamp("$date 00:00:00"))

            currentDateIndex = -1
            for (i in totalDateList.indices) {
                if (date == totalDateList[i]) {
                    currentDateIndex = i
                }
            }
            if (currentDateIndex == -1) {
                timeStamp = oldTimeStamp
                currentDateIndex = oldCurrentDateIndex
                ToastUtils.infoToast(this@AaruyHistoryLogActivity, getString(R.string.no_history_in_day))
                return@setOnClickListener
            } else {
                getSelectHistoryLog(
                    timeStamp!!,
                    true
                )
            }

            dateWeekString = "$dateString $dateToWeek"
            binding.tvDate.text = dateWeekString

            dialog.dismiss()
        }

        btn_negative.setOnClickListener { v ->
            dialog.dismiss()
        }
    }
}