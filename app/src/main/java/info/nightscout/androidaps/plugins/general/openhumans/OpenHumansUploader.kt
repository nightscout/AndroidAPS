package info.nightscout.androidaps.plugins.general.openhumans

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.PowerManager
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import info.nightscout.androidaps.BuildConfig
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.db.*
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.interfaces.PluginDescription
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.logging.L
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.utils.SP
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object OpenHumansUploader : PluginBase(
    PluginDescription()
        .mainType(PluginType.GENERAL)
        .pluginName(R.string.open_humans)
        .shortName(R.string.open_humans_short)
        .description(R.string.donate_your_data_to_science)
        .fragmentClass(OpenHumansFragment::class.qualifiedName)
        .preferencesId(R.xml.pref_openhumans)
), SharedPreferences.OnSharedPreferenceChangeListener {

    private val log = LoggerFactory.getLogger(L.OPENHUMANS)

    const val OPEN_HUMANS_URL = "https://www.openhumans.org"
    const val CLIENT_ID = "oie6DvnaEOagTxSoD6BukkLPwDhVr6cMlN74Ihz1"
    const val CLIENT_SECRET = "jR0N8pkH1jOwtozHc7CsB1UPcJzFN95ldHcK4VGYIApecr8zGJox0v06xLwPLMASScngT12aIaIHXAVCJeKquEXAWG1XekZdbubSpccgNiQBmuVmIF8nc1xSKSNJltCf"
    const val REDIRECT_URL = "androidaps://setup-openhumans"
    const val AUTH_URL = "https://www.openhumans.org/direct-sharing/projects/oauth2/authorize/?client_id=$CLIENT_ID&response_type=code"
    const val WORK_NAME = "Open Humans"
    const val COPY_NOTIFICATION_ID = 3122
    const val FAILURE_NOTIFICATION_ID = 3123
    const val SUCCESS_NOTIFICATION_ID = 3124
    const val SIGNED_OUT_NOTIFICATION_ID = 3125

    private val openHumansAPI = OpenHumansAPI(OPEN_HUMANS_URL, CLIENT_ID, CLIENT_SECRET, REDIRECT_URL)
    private val FILE_NAME_DATE_FORMAT = SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

    private var isSetup
        get() = SP.getBoolean("openhumans_is_setup", false)
        set(value) = SP.putBoolean("openhumans_is_setup", value)
    private var oAuthTokens: OpenHumansAPI.OAuthTokens?
        get() {
            return if (SP.contains("openhumans_access_token") && SP.contains("openhumans_refresh_token") && SP.contains("openhumans_expires_at")) {
                OpenHumansAPI.OAuthTokens(
                    accessToken = SP.getString("openhumans_access_token", null)!!,
                    refreshToken = SP.getString("openhumans_refresh_token", null)!!,
                    expiresAt = SP.getLong("openhumans_expires_at", 0)
                )
            } else {
                null
            }
        }
        set(value) {
            if (value != null) {
                SP.putString("openhumans_access_token", value.accessToken)
                SP.putString("openhumans_refresh_token", value.refreshToken)
                SP.putLong("openhumans_expires_at", value.expiresAt)
            } else {
                SP.remove("openhumans_access_token")
                SP.remove("openhumans_refresh_token")
                SP.remove("openhumans_expires_at")
                SP.remove("openhumans_expires_at")
            }
        }
    var projectMemberId: String?
        get() = SP.getString("openhumans_project_member_id", null)
        private set(value) {
            if (value == null) SP.remove("openhumans_project_member_id")
            else SP.putString("openhumans_project_member_id", value)
        }
    private var uploadCounter: Int
        get() = SP.getInt("openhumans_counter", 1)
        set(value) = SP.putInt("openhumans_counter", value)
    private val appId: UUID
        get() {
            val id = SP.getString("openhumans_appid", null)
            if (id == null) {
                val generated = UUID.randomUUID()
                SP.putString("openhumans_appid", generated.toString())
                return generated
            } else {
                return UUID.fromString(id)
            }
        }

    private var copyDisposable: Disposable? = null

    private val wakeLock = (MainApp.instance().getSystemService(Context.POWER_SERVICE) as PowerManager)
        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AndroidAPS::OpenHumans")

    override fun onStart() {
        super.onStart()
        setupNotificationChannel()
        if (isSetup) scheduleWorker(false)
        SP.sharedPreferences.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onStop() {
        copyDisposable?.dispose()
        cancelWorker()
        SP.sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
        super.onStop()
    }

    fun enqueueBGReading(bgReading: BgReading) = insertQueueItem("BgReadings") {
        put("date", bgReading.date)
        put("isValid", bgReading.isValid)
        put("value", bgReading.value)
        put("direction", bgReading.direction)
        put("raw", bgReading.raw)
        put("source", bgReading.source)
        put("nsId", bgReading._id)
    }

    @JvmOverloads
    fun enqueueCareportalEvent(careportalEvent: CareportalEvent, deleted: Boolean = false) = insertQueueItem("CareportalEvents") {
        put("date", careportalEvent.date)
        put("isValid", careportalEvent.isValid)
        put("source", careportalEvent.source)
        put("nsId", careportalEvent._id)
        put("eventType", careportalEvent.eventType)
        val data = JSONObject(careportalEvent.json)
        val reducedData = JSONObject()
        if (data.has("mgdl")) reducedData.put("mgdl", data.getDouble("mgdl"))
        if (data.has("glucose")) reducedData.put("glucose", data.getDouble("glucose"))
        if (data.has("units")) reducedData.put("units", data.getString("units"))
        if (data.has("created_at")) reducedData.put("created_at", data.getString("created_at"))
        if (data.has("glucoseType")) reducedData.put("glucoseType", data.getString("glucoseType"))
        if (data.has("duration")) reducedData.put("duration", data.getInt("duration"))
        if (data.has("mills")) reducedData.put("mills", data.getLong("mills"))
        if (data.has("eventType")) reducedData.put("eventType", data.getString("eventType"))
        put("data", reducedData)
        put("isDeletion", deleted)
    }

    @JvmOverloads
    fun enqueueExtendedBolus(extendedBolus: ExtendedBolus, deleted: Boolean = false) = insertQueueItem("ExtendedBoluses") {
        put("date", extendedBolus.date)
        put("isValid", extendedBolus.isValid)
        put("source", extendedBolus.source)
        put("nsId", extendedBolus._id)
        put("pumpId", extendedBolus.pumpId)
        put("insulin", extendedBolus.insulin)
        put("durationInMinutes", extendedBolus.durationInMinutes)
        put("isDeletion", deleted)
    }

    @JvmOverloads
    fun enqueueProfileSwitch(profileSwitch: ProfileSwitch, deleted: Boolean = false) = insertQueueItem("ProfileSwitches") {
        put("date", profileSwitch.date)
        put("isValid", profileSwitch.isValid)
        put("source", profileSwitch.source)
        put("nsId", profileSwitch._id)
        put("isCPP", profileSwitch.isCPP)
        put("timeshift", profileSwitch.timeshift)
        put("percentage", profileSwitch.percentage)
        put("profile", JSONObject(profileSwitch.profileJson))
        put("profilePlugin", profileSwitch.profilePlugin)
        put("durationInMinutes", profileSwitch.durationInMinutes)
        put("isDeletion", deleted)
    }

    fun enqueueTotalDailyDose(tdd: TDD) = insertQueueItem("TotalDailyDoses") {
        put("double", tdd.date)
        put("double", tdd.bolus)
        put("double", tdd.basal)
        put("double", tdd.total)
    }

    @JvmOverloads
    fun enqueueTemporaryBasal(temporaryBasal: TemporaryBasal, deleted: Boolean = false) = insertQueueItem("TemporaryBasals") {
        put("date", temporaryBasal.date)
        put("isValid", temporaryBasal.isValid)
        put("source", temporaryBasal.source)
        put("nsId", temporaryBasal._id)
        put("pumpId", temporaryBasal.pumpId)
        put("durationInMinutes", temporaryBasal.durationInMinutes)
        put("durationInMinutes", temporaryBasal.durationInMinutes)
        put("isAbsolute", temporaryBasal.isAbsolute)
        put("percentRate", temporaryBasal.percentRate)
        put("absoluteRate", temporaryBasal.absoluteRate)
        put("isDeletion", deleted)
    }

    @JvmOverloads
    fun enqueueTempTarget(tempTarget: TempTarget, deleted: Boolean = false) = insertQueueItem("TempTargets") {
        put("date", tempTarget.date)
        put("isValid", tempTarget.isValid)
        put("source", tempTarget.source)
        put("nsId", tempTarget._id)
        put("low", tempTarget.low)
        put("high", tempTarget.high)
        put("reason", tempTarget.reason)
        put("durationInMinutes", tempTarget.durationInMinutes)
        put("isDeletion", deleted)
    }

    fun enqueueSMBData(profile: JSONObject, glucoseStatus: JSONObject, iobData: JSONArray, mealData: JSONObject, currentTemp: JSONObject, autosensData: JSONObject, smbAllowed: Boolean, smbAlwaysAllowed: Boolean, result: JSONObject) = insertQueueItem("APSData") {
        put("algorithm", "SMB")
        put("profile", profile)
        put("glucoseStatus", glucoseStatus)
        put("iobData", iobData)
        put("mealData", mealData)
        put("currentTemp", currentTemp)
        put("autosensData", autosensData)
        put("smbAllowed", smbAllowed)
        put("smbAlwaysAllowed", smbAlwaysAllowed)
        put("result", result)
    }

    fun enqueueAMAData(profile: JSONObject, glucoseStatus: JSONObject, iobData: JSONArray, mealData: JSONObject, currentTemp: JSONObject, autosensData: JSONObject, result: JSONObject) = insertQueueItem("APSData") {
        put("algorithm", "AMA")
        put("profile", profile)
        put("glucoseStatus", glucoseStatus)
        put("iobData", iobData)
        put("mealData", mealData)
        put("currentTemp", currentTemp)
        put("autosensData", autosensData)
        put("result", result)
    }

    fun enqueueAMAData(profile: JSONObject, glucoseStatus: JSONObject, iobData: JSONObject, mealData: JSONObject, currentTemp: JSONObject, result: JSONObject) = insertQueueItem("APSData") {
        put("algorithm", "MA")
        put("profile", profile)
        put("glucoseStatus", glucoseStatus)
        put("iobData", iobData)
        put("mealData", mealData)
        put("currentTemp", currentTemp)
        put("result", result)
    }

    private fun insertQueueItem(file: String, structureVersion: Int = 1, generator: JSONObject.() -> Unit) {
        if (oAuthTokens != null && this.isEnabled(PluginType.GENERAL)) {
            try {
                val jsonObject = JSONObject()
                jsonObject.put("structureVersion", structureVersion)
                jsonObject.put("queuedOn", System.currentTimeMillis())
                generator(jsonObject)
                val queueItem = OHQueueItem(
                    file = file,
                    content = jsonObject.toString()
                )
                MainApp.getDbHelper().createOrUpdate(queueItem)
                RxBus.send(OpenHumansFragment.UpdateQueueEvent)
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }
    }

    fun login(authCode: String): Completable =
        openHumansAPI.exchangeAuthToken(authCode)
            .doOnSuccess {
                oAuthTokens = it
            }
            .flatMap { openHumansAPI.getProjectMemberId(it.accessToken) }
            .doOnSuccess {
                projectMemberId = it
                copyExistingDataToQueue()
                RxBus.send(OpenHumansFragment.UpdateViewEvent)
            }
            .ignoreElement()

    fun logout() {
        cancelWorker()
        copyDisposable?.dispose()
        isSetup = false
        oAuthTokens = null
        projectMemberId = null
        MainApp.getDbHelper().clearOpenHumansQueue()
        RxBus.send(OpenHumansFragment.UpdateViewEvent)
    }

    private fun copyExistingDataToQueue() {
        copyDisposable?.dispose()
        var currentProgress = 0L
        var maxProgress = 0L
        val increaseCounter = {
            currentProgress++
            //Updating the notification for every item drastically slows down the operation
            if (currentProgress % 1000L == 0L) showOngoingNotification(maxProgress, currentProgress)
        }
        copyDisposable = Completable.fromCallable { MainApp.getDbHelper().clearOpenHumansQueue() }
            .andThen(Single.defer { Single.just(MainApp.getDbHelper().countOfAllRows) })
            .doOnSuccess { maxProgress = it }
            .flatMapObservable { Observable.defer { Observable.fromIterable(MainApp.getDbHelper().allBgReadings) } }
            .map { enqueueBGReading(it); increaseCounter() }
            .ignoreElements()
            .andThen(Observable.defer { Observable.fromIterable(MainApp.getDbHelper().allCareportalEvents) })
            .map { enqueueCareportalEvent(it); increaseCounter()  }
            .ignoreElements()
            .andThen(Observable.defer { Observable.fromIterable(MainApp.getDbHelper().allExtendedBoluses) })
            .map { enqueueExtendedBolus(it); increaseCounter()  }
            .ignoreElements()
            .andThen(Observable.defer { Observable.fromIterable(MainApp.getDbHelper().allProfileSwitches) })
            .map { enqueueProfileSwitch(it); increaseCounter()  }
            .ignoreElements()
            .andThen(Observable.defer { Observable.fromIterable(MainApp.getDbHelper().allTDDs) })
            .map { enqueueTotalDailyDose(it); increaseCounter()  }
            .ignoreElements()
            .andThen(Observable.defer { Observable.fromIterable(MainApp.getDbHelper().allTemporaryBasals) })
            .map { enqueueTemporaryBasal(it); increaseCounter()  }
            .ignoreElements()
            .andThen(Observable.defer { Observable.fromIterable(MainApp.getDbHelper().allTempTargets) })
            .map { enqueueTempTarget(it); increaseCounter()  }
            .ignoreElements()
            .doOnSubscribe {
                wakeLock.acquire(TimeUnit.MINUTES.toMillis(20))
                showOngoingNotification()
            }
            .doOnComplete {
                isSetup = true
                scheduleWorker(false)
                showSetupFinishedNotification()
            }
            .doOnError {
                showSetupFailedNotification()
            }
            .doFinally {
                copyDisposable = null
                NotificationManagerCompat.from(MainApp.instance()).cancel(COPY_NOTIFICATION_ID)
                wakeLock.release()
            }
            .onErrorComplete()
            .subscribeOn(Schedulers.io())
            .subscribe()
    }

    private fun showOngoingNotification(maxProgress: Long? = null, currentProgress: Long? = null) {
        val notification = NotificationCompat.Builder(MainApp.instance(), "OpenHumans")
            .setContentTitle(MainApp.gs(R.string.finishing_open_humans_setup))
            .setContentText(MainApp.gs(R.string.this_may_take_a_while))
            .setStyle(NotificationCompat.BigTextStyle())
            .setProgress(maxProgress?.toInt() ?: 0, currentProgress?.toInt() ?: 0, maxProgress == null || currentProgress == null)
            .setOngoing(true)
            .setAutoCancel(false)
            .setSmallIcon(R.drawable.notif_icon)
            .build()
        NotificationManagerCompat.from(MainApp.instance()).notify(COPY_NOTIFICATION_ID, notification)
    }

    private fun showSetupFinishedNotification() {
        val notification = NotificationCompat.Builder(MainApp.instance(), "OpenHumans")
            .setContentTitle(MainApp.gs(R.string.setup_finished))
            .setContentText(MainApp.gs(R.string.your_phone_is_upload_data))
            .setStyle(NotificationCompat.BigTextStyle())
            .setSmallIcon(R.drawable.notif_icon)
            .build()
        val notificationManager = NotificationManagerCompat.from(MainApp.instance())
        notificationManager.notify(SUCCESS_NOTIFICATION_ID, notification)
    }

    private fun showSetupFailedNotification() {
        val notification = NotificationCompat.Builder(MainApp.instance(), "OpenHumans")
            .setContentTitle(MainApp.gs(R.string.setup_failed))
            .setContentText(MainApp.gs(R.string.there_was_an_error))
            .setStyle(NotificationCompat.BigTextStyle())
            .setSmallIcon(R.drawable.notif_icon)
            .build()
        val notificationManager = NotificationManagerCompat.from(MainApp.instance())
        notificationManager.notify(FAILURE_NOTIFICATION_ID, notification)
    }

    fun uploadData(): Completable = gatherData()
        .flatMap { data -> refreshAccessTokensIfNeeded().map { accessToken -> accessToken to data } }
        .flatMap { uploadFile(it.first, it.second).andThen(Single.just(it.second)) }
        .flatMapCompletable {
            if (it.highestQueueId != null) {
                removeUploadedEntriesFromQueue(it.highestQueueId)
            } else {
                Completable.complete()
            }
        }
        .doOnError {
            if (it is OpenHumansAPI.OHHttpException && it.code == 401 && it.detail == "Invalid token.") {
                handleSignOut()
            }
        }
        .doOnComplete { RxBus.send(OpenHumansFragment.UpdateQueueEvent) }

    private fun uploadFile(accessToken: String, uploadData: UploadData) = Completable.defer {
        openHumansAPI.prepareFileUpload(accessToken, uploadData.fileName, uploadData.metadata)
            .flatMap { openHumansAPI.uploadFile(it.uploadURL, uploadData.content).andThen(Single.just(it.fileId)) }
            .flatMapCompletable { openHumansAPI.completeFileUpload(accessToken, it) }
    }

    private fun refreshAccessTokensIfNeeded() = Single.defer {
        val oAuthTokens = this.oAuthTokens!!
        if (oAuthTokens.expiresAt <= System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1)) {
            openHumansAPI.refreshAccessToken(oAuthTokens.refreshToken)
                .doOnSuccess { this.oAuthTokens = it }
                .map { it.accessToken }
        } else {
            Single.just(oAuthTokens.accessToken)
        }
    }

    private fun gatherData() = Single.defer {
        val items = MainApp.getDbHelper().allOHQueueItems
        val baos = ByteArrayOutputStream()
        val zos = ZipOutputStream(baos)
        val tags = mutableListOf<String>()

        items.groupBy { it.file }.forEach { entry ->
            tags.add(entry.key)
            val jsonArray = JSONArray()
            entry.value.map { it.content }.forEach { jsonArray.put(JSONObject(it)) }
            zos.writeFile("${entry.key}.json", jsonArray.toString().toByteArray())
        }

        val applicationInfo = JSONObject()
        applicationInfo.put("versionName", BuildConfig.VERSION_NAME)
        applicationInfo.put("versionCode", BuildConfig.VERSION_CODE)
        val hasGitInfo = !BuildConfig.HEAD.endsWith("NoGitSystemAvailable", true)
        val customRemote = !BuildConfig.REMOTE.equals("https://github.com/MilosKozak/AndroidAPS.git", true)
        applicationInfo.put("hasGitInfo", hasGitInfo)
        applicationInfo.put("customRemote", customRemote)
        applicationInfo.put("applicationId", appId.toString())
        zos.writeFile("ApplicationInfo.json", applicationInfo.toString().toByteArray())
        tags.add("ApplicationInfo")

        val preferences = JSONObject(SP.sharedPreferences.all.filterKeys { it.isAllowedKey() })
        zos.writeFile("Preferences.json", preferences.toString().toByteArray())
        tags.add("Preferences")

        val deviceInfo = JSONObject()
        deviceInfo.put("brand", Build.BRAND)
        deviceInfo.put("device", Build.DEVICE)
        deviceInfo.put("manufacturer", Build.MANUFACTURER)
        deviceInfo.put("model", Build.MODEL)
        deviceInfo.put("product", Build.PRODUCT)
        zos.writeFile("DeviceInfo.json", deviceInfo.toString().toByteArray())
        tags.add("DeviceInfo")

        val displayMetrics = DisplayMetrics()
        (MainApp.instance().getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getMetrics(displayMetrics)
        val displayInfo = JSONObject()
        displayInfo.put("height", displayMetrics.heightPixels)
        displayInfo.put("width", displayMetrics.widthPixels)
        displayInfo.put("density", displayMetrics.density)
        displayInfo.put("scaledDensity", displayMetrics.scaledDensity)
        displayInfo.put("xdpi", displayMetrics.xdpi)
        displayInfo.put("ydpi", displayMetrics.ydpi)
        zos.writeFile("DisplayInfo.json", displayInfo.toString().toByteArray())
        tags.add("DisplayInfo")

        val uploadNumber = this.uploadCounter++
        val uploadDate = Date()
        val uploadInfo = JSONObject()
        uploadInfo.put("fileVersion", 1)
        uploadInfo.put("counter", uploadNumber)
        uploadInfo.put("timestamp", uploadDate.time)
        uploadInfo.put("utcOffset", TimeZone.getDefault().getOffset(uploadDate.time))
        zos.writeFile("UploadInfo.json", uploadInfo.toString().toByteArray())
        tags.add("UploadInfo")

        zos.close()
        val bytes = baos.toByteArray()

        Single.just(UploadData(
            fileName = "upload-num$uploadNumber-ver1-date${FILE_NAME_DATE_FORMAT.format(uploadDate)}-appid${appId.toString().replace("-", "")}.zip",
            metadata = OpenHumansAPI.FileMetadata(
                tags = tags,
                description = "AndroidAPS Database Upload",
                md5 = MessageDigest.getInstance("MD5").digest(bytes).toHexString(),
                creationDate = uploadDate.time
            ),
            content = bytes,
            highestQueueId = items.map { it.id }.max()
        ))
    }

    private fun ZipOutputStream.writeFile(name: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(bytes)
        closeEntry()
    }

    private fun removeUploadedEntriesFromQueue(highestId: Long) = Completable.fromCallable {
        MainApp.getDbHelper().removeAllOHQueueItemsWithIdSmallerThan(highestId)
    }

    private fun handleSignOut() {
        val notification = NotificationCompat.Builder(MainApp.instance(), "OpenHumans")
            .setContentTitle(MainApp.gs(R.string.you_have_been_signed_out_of_open_humans))
            .setContentText(MainApp.gs(R.string.click_here_to_sign_in_again_if_this_wasnt_on_purpose))
            .setStyle(NotificationCompat.BigTextStyle())
            .setSmallIcon(R.drawable.notif_icon)
            .setAutoCancel(true)
            .setContentIntent(PendingIntent.getActivity(
                MainApp.instance(),
                0,
                Intent(MainApp.instance(), OpenHumansLoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                0
            ))
            .build()
        NotificationManagerCompat.from(MainApp.instance()).notify(SIGNED_OUT_NOTIFICATION_ID, notification)
        logout()
    }

    private fun cancelWorker() {
        WorkManager.getInstance(MainApp.instance()).cancelUniqueWork(WORK_NAME)
    }

    private fun scheduleWorker(replace: Boolean) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresCharging(SP.getBoolean("key_oh_charging_only", false))
            .build()
        val workRequest = PeriodicWorkRequestBuilder<OHUploadWorker>(1, TimeUnit.DAYS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(MainApp.instance()).enqueueUniquePeriodicWork(WORK_NAME, if (replace) ExistingPeriodicWorkPolicy.REPLACE else ExistingPeriodicWorkPolicy.KEEP, workRequest)
    }

    private fun setupNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManagerCompat = NotificationManagerCompat.from(MainApp.instance())
            notificationManagerCompat.createNotificationChannel(NotificationChannel(
                "OpenHumans",
                MainApp.gs(R.string.open_humans),
                NotificationManager.IMPORTANCE_DEFAULT
            ))
        }
    }

    private class UploadData(
        val fileName: String,
        val metadata: OpenHumansAPI.FileMetadata,
        val content: ByteArray,
        val highestQueueId: Long?
    )

    private val HEX_DIGITS = "0123456789ABCDEF".toCharArray()

    private fun ByteArray.toHexString(): String {
        val stringBuilder = StringBuilder()
        map { it.toInt() }.forEach {
            stringBuilder.append(HEX_DIGITS[(it shr 4) and 0x0F])
            stringBuilder.append(HEX_DIGITS[it and 0x0F])
        }
        return stringBuilder.toString()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        if (key == "key_oh_charging_only" && isSetup) scheduleWorker(true)
    }
}