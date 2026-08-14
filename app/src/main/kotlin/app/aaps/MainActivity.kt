package app.aaps

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.text.InputType
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.style.ForegroundColorSpan
import android.text.util.Linkify
import android.util.TypedValue
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.core.view.MenuCompat
import androidx.core.view.MenuProvider
import app.aaps.activities.HistoryBrowseActivity
import app.aaps.activities.PreferencesActivity
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.maintenance.FileListProvider
import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.protection.ExportPasswordDataStore
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.events.EventAppInitialized
import app.aaps.core.interfaces.rx.events.EventPreferenceChange
import app.aaps.core.interfaces.rx.events.EventRebuildTabs
import app.aaps.core.interfaces.smsCommunicator.SmsCommunicator
import app.aaps.core.interfaces.ui.IconsProvider
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.interfaces.versionChecker.VersionCheckerUtils
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.StringKey
import app.aaps.core.objects.crypto.CryptoUtil
import app.aaps.core.ui.UIRunnable
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.core.ui.locale.LocaleHelper
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.core.utils.isRunningRealPumpTest
import app.aaps.databinding.ActivityMainBinding
import app.aaps.plugins.configuration.activities.DaggerAppCompatActivityWithResult
import app.aaps.plugins.configuration.activities.SingleFragmentActivity
import app.aaps.plugins.configuration.maintenance.MaintenancePlugin
import app.aaps.plugins.configuration.setupwizard.SetupWizardActivity
import app.aaps.plugins.constraints.signatureVerifier.SignatureVerifierPlugin
import app.aaps.ui.activities.ProfileHelperActivity
import app.aaps.ui.activities.StatsActivity
import app.aaps.ui.activities.TreatmentsActivity
import app.aaps.ui.tabs.TabPageAdapter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.joanzapata.iconify.Iconify
import com.joanzapata.iconify.fonts.FontAwesomeModule
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import java.util.Locale
import javax.inject.Inject

class MainActivity : DaggerAppCompatActivityWithResult() {

    private val disposable = CompositeDisposable()

    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var versionCheckerUtils: VersionCheckerUtils
    @Inject lateinit var smsCommunicator: SmsCommunicator
    @Inject lateinit var loop: Loop
    @Inject lateinit var config: Config
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var protectionCheck: ProtectionCheck
    @Inject lateinit var iconsProvider: IconsProvider
    @Inject lateinit var constraintChecker: ConstraintsChecker
    @Inject lateinit var signatureVerifierPlugin: SignatureVerifierPlugin
    @Inject lateinit var maintenancePlugin: MaintenancePlugin
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var fileListProvider: FileListProvider
    @Inject lateinit var cryptoUtil: CryptoUtil
    @Inject lateinit var exportPasswordDataStore: ExportPasswordDataStore
    @Inject lateinit var uiInteraction: UiInteraction
    @Inject lateinit var configBuilder: ConfigBuilder

    private var actionBarDrawerToggle: ActionBarDrawerToggle? = null
    private var pluginPreferencesMenuItem: MenuItem? = null
    private var menu: Menu? = null
    private var menuOpen = false
    private var isProtectionCheckActive = false
    private lateinit var binding: ActivityMainBinding
    private var mainMenuProvider: MenuProvider? = null

    // 365天过期毫秒常量(变量名保留旧名 EXPIRE_15DAY_MS,避免大范围改名)
    private val EXPIRE_15DAY_MS = 365L * 24 * 60 * 60 * 1000

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Iconify.with(FontAwesomeModule())
        LocaleHelper.update(applicationContext)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeButtonEnabled(true)

        initAllComponents(savedInstanceState)

        val prefs = getSharedPreferences("AppLock", Context.MODE_PRIVATE)
        val verified = prefs.getBoolean("password_verified", false)
        val hasTotpSecret = prefs.getString("totp_secret", null) != null
        val lastVerifyTs = prefs.getLong("last_verify_time", 0L)
        val nowTs = System.currentTimeMillis()
        val isExpired = lastVerifyTs > 0 && (nowTs - lastVerifyTs >= EXPIRE_15DAY_MS)

        // 修复脏数据
        if (!hasTotpSecret && verified) {
            prefs.edit()
                .putBoolean("password_verified", false)
                .remove("last_verify_time")
                .apply()
        }

        // 未验证 或 365天过期，强制弹窗拦截全部APP流程
        if (!verified || isExpired) {
            Handler(Looper.getMainLooper()).postDelayed({
                                                            if (initTotpSecretIfNeeded()) {
                                                                showPasswordVerificationDialog()
                                                            }
                                                        }, 200)
            return
        }

        // 验证有效，启动APP主页+设置向导
        Handler(Looper.getMainLooper()).postDelayed({
                                                        start()
                                                    }, 200)
    }

    /**
     * 原生TOTP工具，无第三方依赖
     */
    private object TotpUtils {
        private const val DEFAULT_SECRET_SIZE = 20
        private const val DEFAULT_CODE_DIGITS = 6
        private const val DEFAULT_TIME_STEP = 30L
        private const val DEFAULT_TOLERANCE = 1

        private val BASE32_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ2345678".toCharArray()
        private val BASE32_MAP = BASE32_CHARS.withIndex().associate { it.value to it.index }.toMap()

        fun generateSecret(): String {
            val random = java.security.SecureRandom()
            val secret = ByteArray(DEFAULT_SECRET_SIZE)
            random.nextBytes(secret)
            return encodeBase32(secret)
        }

        fun generateTotp(secretBase32: String, time: Long = System.currentTimeMillis() / 1000L): String {
            val secret = decodeBase32(secretBase32)
            val counter = time / DEFAULT_TIME_STEP
            val counterBytes = ByteArray(8)
            for (i in 7 downTo 0) {
                counterBytes[i] = (counter shr (8 * (7 - i))).toByte()
            }

            val mac = javax.crypto.Mac.getInstance("HmacSHA1")
            mac.init(javax.crypto.spec.SecretKeySpec(secret, "HmacSHA1"))
            val hash = mac.doFinal(counterBytes)

            val offset = hash[hash.size - 1].toInt() and 0xF
            var binary = (hash[offset].toInt() and 0x7F) shl 24
            binary = binary or ((hash[offset + 1].toInt() and 0xFF) shl 16)
            binary = binary or ((hash[offset + 2].toInt() and 0xFF) shl 8)
            binary = binary or (hash[offset + 3].toInt() and 0xFF)

            val otp = binary % 1000000
            return otp.toString().padStart(DEFAULT_CODE_DIGITS, '0')
        }

        fun verifyTotp(secretBase32: String, inputCode: String, tolerance: Int = DEFAULT_TOLERANCE): Boolean {
            val currentTime = System.currentTimeMillis() / 1000L
            for (offset in -tolerance..tolerance) {
                val time = currentTime + offset * DEFAULT_TIME_STEP
                val expectedCode = generateTotp(secretBase32, time)
                if (expectedCode == inputCode) return true
            }
            return false
        }

        private fun encodeBase32(data: ByteArray): String {
            val output = StringBuilder()
            var i = 0
            var n = 0
            var bits = 0
            while (i < data.size) {
                n = n shl 8 or (data[i].toInt() and 0xFF)
                bits += 8
                while (bits >= 5) {
                    bits -= 5
                    output.append(BASE32_CHARS[n shr bits])
                    n = n and ((1 shl bits) - 1)
                }
                i++
            }
            if (bits > 0) {
                n = n shl (5 - bits)
                output.append(BASE32_CHARS[n])
            }
            return output.toString()
        }

        private fun decodeBase32(encoded: String): ByteArray {
            val cleanEncoded = encoded.uppercase().replace("=", "")
            val output = mutableListOf<Byte>()
            var i = 0
            var n = 0
            var bits = 0
            for (c in cleanEncoded) {
                val value = BASE32_MAP[c] ?: throw IllegalArgumentException("无效Base32字符")
                n = n shl 5 or value
                bits += 5
                if (bits >= 8) {
                    bits -= 8
                    output.add((n shr bits).toByte())
                    n = n and ((1 shl bits) - 1)
                }
            }
            return output.toByteArray()
        }
    }

    /**
     * 密钥弹窗
     * showKeyOnly=true:仅展示当前密钥信息+提示(从"获取密钥"进入),不输码、不重新生成
     * showKeyOnly=false:首次激活流程,生成密钥+输码验证
     */
    private fun initTotpSecretIfNeeded(showKeyOnly: Boolean = false): Boolean {
        val prefs = getSharedPreferences("AppLock", Context.MODE_PRIVATE)
        val existingSecret = prefs.getString("totp_secret", null)
        if (!showKeyOnly && existingSecret != null) return true

        val secretBase32 = existingSecret ?: TotpUtils.generateSecret()
        if (existingSecret == null) prefs.edit().putString("totp_secret", secretBase32).apply()

        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp2px(16), dp2px(16), dp2px(16), dp2px(16))

            addView(TextView(this@MainActivity).apply {
                text = if (showKeyOnly) {
                    "当前授权密钥：\n1.截图此页面32位密钥\n2.联系管理员，发送密钥截图\n3.获取6位授权码（授权码30秒内有效）"
                } else {
                    "首次使用请先获取动态密钥：\n1.截图此页面32位密钥\n2.联系管理员，发送密钥截图\n3.获取6位授权码（授权码30秒内有效）"
                }
                textSize = 14f
            })

            addView(TextView(this@MainActivity).apply {
                text = "密钥：$secretBase32"
                textSize = 18f
                setTextColor(Color.RED)
                setPadding(0, dp2px(16), 0, dp2px(16))
            })

            if (showKeyOnly) {
                addView(TextView(this@MainActivity).apply {
                    text = authStatusText()
                    textSize = 14f
                    setTextColor(Color.parseColor("#FF8C00"))
                    setPadding(0, 0, 0, dp2px(8))
                })
            }

            if (!showKeyOnly) {
                addView(EditText(this@MainActivity).apply {
                    id = android.R.id.input
                    inputType = InputType.TYPE_CLASS_NUMBER
                    hint = "请输入获取的动态授权码"
                    maxLines = 1
                })
            }
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(if (showKeyOnly) "密钥信息" else "获取授权码")
            .setView(dialogView)
            .setCancelable(false)
            .setPositiveButton(if (showKeyOnly) "知道了" else "确认") { dialog, _ ->
                if (showKeyOnly) {
                    dialog.dismiss()
                    return@setPositiveButton
                }
                val inputCode = dialogView.findViewById<EditText>(android.R.id.input).text.toString()
                val secret = prefs.getString("totp_secret", null) ?: return@setPositiveButton

                if (TotpUtils.verifyTotp(secret, inputCode)) {
                    ToastUtils.okToast(this, "授权码输入成功！365天后需要重新验证")
                    // 首次验证写入时间戳
                    prefs.edit()
                        .putBoolean("password_verified", true)
                        .putLong("last_verify_time", System.currentTimeMillis())
                        .apply()
                    // 首次验证成功后直接进入,不再重复弹第二次验证框
                    dialog.dismiss()
                    Handler(Looper.getMainLooper()).post { start() }
                } else {
                    ToastUtils.errorToast(this, "授权码错误，重新生成密钥")
                    prefs.edit().remove("totp_secret").apply()
                    initTotpSecretIfNeeded()
                }
            }
            .setNegativeButton(if (showKeyOnly) "返回" else "退出") { dialog, _ ->
                if (showKeyOnly) dialog.dismiss() else finish()
            }
            .show()

        return false
    }

    /**
     * 重置密钥，清空验证状态与过期时间
     */
    private fun resetTotpSecret() {
        val prefs = getSharedPreferences("AppLock", Context.MODE_PRIVATE)
        prefs.edit()
            .remove("totp_secret")
            .remove("password_verified")
            .remove("last_verify_time")
            .apply()
        initTotpSecretIfNeeded()
    }

    /**
     * 授权状态文本:尚未验证 / 剩余X天 / 已过期X天
     */
    private fun authStatusText(): String {
        val prefs = getSharedPreferences("AppLock", Context.MODE_PRIVATE)
        val lastTs = prefs.getLong("last_verify_time", 0L)
        if (lastTs == 0L) return "授权状态：尚未验证"
        val remainMs = EXPIRE_15DAY_MS - (System.currentTimeMillis() - lastTs)
        val days = remainMs / (24 * 60 * 60 * 1000)
        return if (remainMs > 0) "授权状态：已验证，剩余 $days 天"
        else "授权状态：已过期 ${-days} 天，请重新验证"
    }

    /**
     * 全局置顶密码弹窗（带忘记密码重置）
     */
    private fun showPasswordVerificationDialog() {
        val maskView = View(this)
        maskView.layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        maskView.setBackgroundColor(Color.parseColor("#CC000000"))
        maskView.isClickable = true
        val rootView = window.decorView.findViewById<FrameLayout>(android.R.id.content)
        rootView.addView(maskView)

        val passwordInput = EditText(this)
        passwordInput.inputType = InputType.TYPE_CLASS_NUMBER
        passwordInput.hint = "请输入管理员发送的授权码"
        val padding = dp2px(16)
        passwordInput.setPadding(padding, padding, padding, padding)

        val dialogLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@MainActivity).apply {
                text = authStatusText()
                textSize = 13f
                setTextColor(Color.parseColor("#FF8C00"))
                setPadding(0, 0, 0, dp2px(8))
            })
            addView(passwordInput)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("APP授权码验证")
            .setView(dialogLayout)
            .setCancelable(false)
            .setNeutralButton("获取密钥") { _, _ ->
                // 不再重置密钥:有密钥则只展示密钥信息(不输码、不重新生成),无密钥走首次激活
                val secret = getSharedPreferences("AppLock", Context.MODE_PRIVATE).getString("totp_secret", null)
                if (secret == null) initTotpSecretIfNeeded() else initTotpSecretIfNeeded(showKeyOnly = true)
            }
            .setNegativeButton("退出") { _, _ -> finish() }
            .setPositiveButton("验证") { dialog, _ ->
                val inputPwd = passwordInput.text.toString()
                val prefs = getSharedPreferences("AppLock", Context.MODE_PRIVATE)
                val secret = prefs.getString("totp_secret", null) ?: return@setPositiveButton

                if (TotpUtils.verifyTotp(secret, inputPwd)) {
                    // 验证成功刷新过期时间
                    prefs.edit()
                        .putBoolean("password_verified", true)
                        .putLong("last_verify_time", System.currentTimeMillis())
                        .apply()
                    rootView.removeView(maskView)
                    dialog.dismiss()
                    ToastUtils.okToast(this, "验证成功，365天后将再次校验")
                    Handler(Looper.getMainLooper()).post { start() }
                } else {
                    ToastUtils.errorToast(this, "动态密码错误，请联系管理员")
                    dialog.dismiss()
                    showPasswordVerificationDialog()
                }
            }
            .show()
    }

    private fun dp2px(dp: Int): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics).toInt()
    }

    private fun initAllComponents(savedInstanceState: Bundle?) {
        actionBarDrawerToggle = ActionBarDrawerToggle(this, binding.mainDrawerLayout, R.string.open_navigation, R.string.close_navigation).also {
            binding.mainDrawerLayout.addDrawerListener(it)
            it.syncState()
        }

        processPreferenceChange(EventPreferenceChange(BooleanKey.OverviewKeepScreenOn.key))

        disposable += rxBus
            .toObservable(EventRebuildTabs::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({
                           if (it.recreate) recreate()
                           else setupViews()
                           setWakeLock()
                       }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventPreferenceChange::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ processPreferenceChange(it) }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventAppInitialized::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({
                           // 授权锁:未验证/过期不启动主页与向导,避免覆盖授权弹窗
                           val prefs = getSharedPreferences("AppLock", Context.MODE_PRIVATE)
                           val verified = prefs.getBoolean("password_verified", false)
                           val lastTs = prefs.getLong("last_verify_time", 0)
                           val expired = lastTs > 0 && (System.currentTimeMillis() - lastTs >= EXPIRE_15DAY_MS)
                           if (verified && !expired) start()
                       }, fabricPrivacy::logException)

        // 返回键加固：未验证/过期直接退出，无法绕过弹窗
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val prefs = getSharedPreferences("AppLock", Context.MODE_PRIVATE)
                val verified = prefs.getBoolean("password_verified", false)
                val lastTs = prefs.getLong("last_verify_time",0)
                val expired = lastTs>0 && (System.currentTimeMillis()-lastTs >= EXPIRE_15DAY_MS)
                if (!verified || expired) {
                    finish()
                    return
                }
                if (binding.mainDrawerLayout.isDrawerOpen(GravityCompat.START))
                    binding.mainDrawerLayout.closeDrawers()
                else if (menuOpen)
                    menu?.close()
                else if (binding.mainPager.currentItem != 0)
                    binding.mainPager.currentItem = 0
                else finish()
            }
        })

        mainMenuProvider = object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                MenuCompat.setGroupDividerEnabled(menu, true)
                this@MainActivity.menu = menu
                menuInflater.inflate(R.menu.menu_main, menu)
                pluginPreferencesMenuItem = menu.findItem(R.id.nav_plugin_preferences)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean =
                when (menuItem.itemId) {
                    R.id.nav_preferences -> {
                        protectionCheck.queryProtection(this@MainActivity, ProtectionCheck.Protection.PREFERENCES, {
                            startActivity(Intent(this@MainActivity, PreferencesActivity::class.java).setAction("info.nightscout.androidaps.MainActivity"))
                        })
                        true
                    }
                    R.id.nav_historybrowser -> {
                        startActivity(Intent(this@MainActivity, HistoryBrowseActivity::class.java).setAction("info.nightscout.androidaps.MainActivity"))
                        true
                    }
                    R.id.nav_treatments -> {
                        startActivity(Intent(this@MainActivity, TreatmentsActivity::class.java).setAction("info.nightscout.androidaps.MainActivity"))
                        true
                    }
                    R.id.nav_setupwizard -> {
                        protectionCheck.queryProtection(this@MainActivity, ProtectionCheck.Protection.PREFERENCES, {
                            startActivity(Intent(this@MainActivity, SetupWizardActivity::class.java).setAction("info.nightscout.androidaps.MainActivity"))
                        })
                        true
                    }
                    R.id.nav_about -> {
                        var message = "Build: ${config.BUILD_VERSION}\n"
                        message += "Flavor: ${BuildConfig.FLAVOR}${BuildConfig.BUILD_TYPE}\n"
                        message += "${rh.gs(app.aaps.plugins.configuration.R.string.configbuilder_nightscoutversion_label)} ${activePlugin.activeNsClient?.detectedNsVersion() ?: rh.gs(app.aaps.plugins.main.R.string.not_available_full)}"
                        if (config.isEngineeringMode()) message += "\n${rh.gs(app.aaps.plugins.configuration.R.string.engineering_mode_enabled)}"
                        if (config.isUnfinishedMode()) message += "\nUnfinished mode enabled"
                        if (!fabricPrivacy.fabricEnabled()) message += "\n${rh.gs(app.aaps.core.ui.R.string.fabric_upload_disabled)}"
                        message += "\n" + authStatusText()
                        message += rh.gs(app.aaps.core.ui.R.string.about_link_urls)
                        val messageSpanned = SpannableString(message)
                        Linkify.addLinks(messageSpanned, Linkify.WEB_URLS)
                        MaterialAlertDialogBuilder(this@MainActivity)
                            .setTitle(rh.gs(R.string.app_name) + " " + config.VERSION)
                            .setIcon(iconsProvider.getIcon())
                            .setMessage(messageSpanned)
                            .setPositiveButton(rh.gs(app.aaps.core.ui.R.string.ok), null)
                            .setNeutralButton(rh.gs(app.aaps.core.ui.R.string.cta_dont_kill_my_app_info)) { _, _ ->
                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://dontkillmyapp.com/" + Build.MANUFACTURER.lowercase().replace(" ", "-"))))
                            }
                            .setNegativeButton("重置授权码") { _, _ ->
                                MaterialAlertDialogBuilder(this@MainActivity)
                                    .setTitle("确认重置")
                                    .setMessage("重置后清除当前授权码，需要重新设置，是否继续？")
                                    .setPositiveButton("确认") { _, _ -> resetTotpSecret() }
                                    .setNegativeButton("取消", null)
                                    .show()
                            }
                            .create().apply {
                                show()
                                findViewById<TextView>(android.R.id.message)?.movementMethod = LinkMovementMethod.getInstance()
                            }
                        true
                    }
                    R.id.nav_exit -> {
                        finish()
                        configBuilder.exitApp("Menu", Sources.Aaps, false)
                        true
                    }
                    R.id.nav_plugin_preferences -> {
                        val plugin = (binding.mainPager.adapter as TabPageAdapter).getPluginAt(binding.mainPager.currentItem)
                        protectionCheck.queryProtection(this@MainActivity, ProtectionCheck.Protection.PREFERENCES, {
                            startActivity(Intent(this@MainActivity, PreferencesActivity::class.java).setAction("info.nightscout.androidaps.MainActivity").putExtra(UiInteraction.PLUGIN_NAME, plugin.javaClass.simpleName))
                        })
                        true
                    }
                    R.id.nav_defaultprofile -> {
                        startActivity(Intent(this@MainActivity, ProfileHelperActivity::class.java).setAction("info.nightscout.androidaps.MainActivity"))
                        true
                    }
                    R.id.nav_stats -> {
                        startActivity(Intent(this@MainActivity, StatsActivity::class.java).setAction("info.nightscout.androidaps.MainActivity"))
                        true
                    }
                    else -> actionBarDrawerToggle?.onOptionsItemSelected(menuItem)!!
                }
        }
        mainMenuProvider?.let { addMenuProvider(it) }
        if (config.appInitialized) setupViews()
    }

    private fun start() {
        binding.splash.visibility = View.GONE
        setUserStats()
        setupViews()

        if (startWizard() && !isRunningRealPumpTest()) {
            protectionCheck.queryProtection(this, ProtectionCheck.Protection.PREFERENCES, {
                startActivity(Intent(this, SetupWizardActivity::class.java).setAction("info.nightscout.androidaps.MainActivity"))
            })
        }
        androidPermission.notifyForStoragePermission(this)
        androidPermission.notifyForBatteryOptimizationPermission(this)
        if (!config.AAPSCLIENT) androidPermission.notifyForLocationPermissions(this)
        if (config.PUMPDRIVERS) {
            if (smsCommunicator.isEnabled()) androidPermission.notifyForSMSPermissions(this)
            androidPermission.notifyForSystemWindowPermissions(this)
            androidPermission.notifyForBtConnectPermission(this)
        }
        passwordResetCheck(this)
        exportPasswordResetCheck(this)

        if (config.isDev() && preferences.get(StringKey.MaintenanceIdentification).isBlank())
            uiInteraction.addNotificationWithAction(
                id = Notification.IDENTIFICATION_NOT_SET,
                text = rh.gs(R.string.identification_not_set),
                level = Notification.INFO,
                buttonText = R.string.set,
                action = Runnable {
                    preferences.put(BooleanKey.GeneralSimpleMode, false)
                    startActivity(Intent(this@MainActivity, PreferencesActivity::class.java).setAction("info.nightscout.androidaps.MainActivity").putExtra(UiInteraction.PLUGIN_NAME, MaintenancePlugin::class.java.simpleName))
                },
                validityCheck = { config.isDev() && preferences.get(StringKey.MaintenanceIdentification).isBlank() }
            )

        if (preferences.get(StringKey.ProtectionMasterPassword) == "")
            uiInteraction.addNotificationWithAction(
                id = Notification.MASTER_PASSWORD_NOT_SET,
                text = rh.gs(app.aaps.core.ui.R.string.master_password_not_set),
                level = Notification.NORMAL,
                buttonText = R.string.set,
                action = { startActivity(Intent(this@MainActivity, PreferencesActivity::class.java).setAction("info.nightscout.androidaps.MainActivity").putExtra(UiInteraction.PREFERENCE, UiInteraction.Preferences.PROTECTION)) },
                validityCheck = { preferences.get(StringKey.ProtectionMasterPassword) == "" }
            )
        if (preferences.getIfExists(StringKey.AapsDirectoryUri).isNullOrEmpty())
            uiInteraction.addNotificationWithAction(
                id = Notification.AAPS_DIR_NOT_SELECTED,
                text = rh.gs(app.aaps.core.ui.R.string.aaps_directory_not_selected),
                level = Notification.IMPORTANCE_HIGH,
                buttonText = R.string.select,
                action = { maintenancePlugin.selectAapsDirectory(this) },
                validityCheck = { preferences.getIfExists(StringKey.AapsDirectoryUri).isNullOrEmpty() }
            )
    }

    private fun startWizard(): Boolean = !preferences.get(BooleanKey.GeneralSetupWizardProcessed)

    override fun onPostCreate(savedInstanceState: Bundle?, persistentState: PersistableBundle?) {
        super.onPostCreate(savedInstanceState, persistentState)
        actionBarDrawerToggle?.syncState()
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.mainPager.adapter = null
        binding.mainDrawerLayout.removeDrawerListener(actionBarDrawerToggle!!)
        mainMenuProvider?.let { removeMenuProvider(it) }
        disposable.clear()
    }

    override fun onResume() {
        super.onResume()
        val prefs = getSharedPreferences("AppLock", Context.MODE_PRIVATE)
        val verified = prefs.getBoolean("password_verified", false)
        val lastTs = prefs.getLong("last_verify_time",0)
        val expired = lastTs>0 && (System.currentTimeMillis()-lastTs >= EXPIRE_15DAY_MS)
        if (config.appInitialized && verified && !expired) binding.splash.visibility = View.GONE
        if (!isProtectionCheckActive && verified && !expired) {
            isProtectionCheckActive = true
            protectionCheck.queryProtection(this, ProtectionCheck.Protection.APPLICATION, UIRunnable { isProtectionCheckActive = false },
                                            UIRunnable { OKDialog.show(this, "", rh.gs(R.string.authorizationfailed), true) { isProtectionCheckActive = false; finish() } },
                                            UIRunnable { OKDialog.show(this, "", rh.gs(R.string.authorizationfailed), true) { isProtectionCheckActive = false; finish() } }
            )
        }
    }

    private fun setWakeLock() {
        val keepScreenOn = preferences.get(BooleanKey.OverviewKeepScreenOn)
        if (keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun processPreferenceChange(ev: EventPreferenceChange) {
        if (ev.isChanged(BooleanKey.OverviewKeepScreenOn.key)) setWakeLock()
        if (ev.isChanged(StringKey.GeneralSkin.key)) recreate()
    }

    private fun setupViews() {
        val pageAdapter = TabPageAdapter(this)
        binding.mainNavigationView.setNavigationItemSelectedListener { true }
        val menu = binding.mainNavigationView.menu.also { it.clear() }
        for (p in activePlugin.getPluginsList())
            if (p.isEnabled() && p.hasFragment() && p.showInList(p.getType())) {
                if ((preferences.simpleMode && p.pluginDescription.simpleModePosition == PluginDescription.Position.TAB) || (!preferences.simpleMode && p.isFragmentVisible()))
                    pageAdapter.registerNewFragment(p)
                if ((preferences.simpleMode && !p.pluginDescription.neverVisible && p.pluginDescription.simpleModePosition == PluginDescription.Position.MENU) || (!preferences.simpleMode && !p.pluginDescription.neverVisible && !p.isFragmentVisible())) {
                    val menuItem = menu.add(p.name)
                    menuItem.isCheckable = true
                    if (p.menuIcon != -1) menuItem.setIcon(p.menuIcon) else menuItem.setIcon(app.aaps.core.ui.R.drawable.ic_settings)
                    menuItem.setOnMenuItemClickListener {
                        startActivity(Intent(this, SingleFragmentActivity::class.java).setAction(this::class.simpleName).putExtra("plugin", activePlugin.getPluginsList().indexOf(p)))
                        binding.mainDrawerLayout.closeDrawers()
                        true
                    }
                }
            }
        binding.mainPager.adapter = pageAdapter
        binding.mainPager.offscreenPageLimit = 8

        if (preferences.get(BooleanKey.OverviewShortTabTitles)) {
            binding.tabsNormal.visibility = View.GONE
            binding.tabsCompact.visibility = View.VISIBLE
            binding.toolbar.layoutParams = LinearLayout.LayoutParams(Toolbar.LayoutParams.MATCH_PARENT, resources.getDimension(app.aaps.core.ui.R.dimen.compact_height).toInt())
            TabLayoutMediator(binding.tabsCompact, binding.mainPager) { tab, position ->
                tab.text = (binding.mainPager.adapter as TabPageAdapter).getPluginAt(position).nameShort
            }.attach()
        } else {
            binding.tabsNormal.visibility = View.VISIBLE
            binding.tabsCompact.visibility = View.GONE
            val typedValue = TypedValue()
            if (theme.resolveAttribute(android.R.attr.actionBarSize, typedValue, true)) {
                binding.toolbar.layoutParams = LinearLayout.LayoutParams(
                    Toolbar.LayoutParams.MATCH_PARENT,
                    TypedValue.complexToDimensionPixelSize(typedValue.data, resources.displayMetrics)
                )
            }
            TabLayoutMediator(binding.tabsNormal, binding.mainPager) { tab, position ->
                tab.text = (binding.mainPager.adapter as TabPageAdapter).getPluginAt(position).name
            }.attach()
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        return super.dispatchTouchEvent(event)
    }

    override fun onMenuOpened(featureId: Int, menu: Menu): Boolean {
        menuOpen = true
        if (binding.mainDrawerLayout.isDrawerOpen(GravityCompat.START)) binding.mainDrawerLayout.closeDrawers()
        val result = super.onMenuOpened(featureId, menu)
        menu.findItem(R.id.nav_treatments)?.isEnabled = profileFunction.getProfile() != null
        if (binding.mainPager.currentItem >= 0) {
            val plugin = (binding.mainPager.adapter as TabPageAdapter?)?.getPluginAt(binding.mainPager.currentItem) ?: return result
            this.menu?.findItem(R.id.nav_plugin_preferences)?.title = rh.gs(R.string.nav_preferences_plugin, plugin.name)
            pluginPreferencesMenuItem?.isEnabled = (binding.mainPager.adapter as TabPageAdapter).getPluginAt(binding.mainPager.currentItem).preferencesId != PluginDescription.PREFERENCE_NONE
        }
        if (pluginPreferencesMenuItem?.isEnabled == false) {
            val spanString = SpannableString(this.menu?.findItem(R.id.nav_plugin_preferences)?.title.toString())
            spanString.setSpan(ForegroundColorSpan(rh.gac(app.aaps.core.ui.R.attr.disabledTextColor)), 0, spanString.length, 0)
            this.menu?.findItem(R.id.nav_plugin_preferences)?.title = spanString
        }
        return result
    }

    override fun onPanelClosed(featureId: Int, menu: Menu) {
        menuOpen = false
        super.onPanelClosed(featureId, menu)
    }

    private fun setUserStats() {
        if (!fabricPrivacy.fabricEnabled()) return
        val closedLoopEnabled = if (constraintChecker.isClosedLoopAllowed().value()) "CLOSED_LOOP_ENABLED" else "CLOSED_LOOP_DISABLED"
        val remote = config.REMOTE.lowercase(Locale.getDefault()).replace("https://", "").replace("http://", "").replace(".git", "").replace(".com/", ":").replace(".org/", ":").replace(".net/", ":")
        fabricPrivacy.setUserProperty("Mode", config.APPLICATION_ID + "-" + closedLoopEnabled)
        fabricPrivacy.setUserProperty("Language", preferences.getIfExists(StringKey.GeneralLanguage) ?: Locale.getDefault().language)
        fabricPrivacy.setUserProperty("Version", config.VERSION_NAME)
        fabricPrivacy.setUserProperty("HEAD", BuildConfig.HEAD)
        fabricPrivacy.setUserProperty("Remote", remote)
        val hashes = signatureVerifierPlugin.shortHashes()
        if (hashes.isNotEmpty()) fabricPrivacy.setUserProperty("Hash", hashes[0])
        activePlugin.activePump.let { fabricPrivacy.setUserProperty("Pump", it::class.java.simpleName) }
        if (!config.AAPSCLIENT && !config.PUMPCONTROL) activePlugin.activeAPS.let { fabricPrivacy.setUserProperty("Aps", it::class.java.simpleName) }
        activePlugin.activeBgSource.let { fabricPrivacy.setUserProperty("BgSource", it::class.java.simpleName) }
        fabricPrivacy.setUserProperty("Profile", activePlugin.activeProfileSource.javaClass.simpleName)
        activePlugin.activeSensitivity.let { fabricPrivacy.setUserProperty("Sensitivity", it::class.java.simpleName) }
        activePlugin.activeInsulin.let { fabricPrivacy.setUserProperty("Insulin", it::class.java.simpleName) }

        FirebaseCrashlytics.getInstance().setCustomKey("HEAD", BuildConfig.HEAD)
        FirebaseCrashlytics.getInstance().setCustomKey("Version", config.VERSION_NAME)
        FirebaseCrashlytics.getInstance().setCustomKey("BuildType", config.BUILD_TYPE)
        FirebaseCrashlytics.getInstance().setCustomKey("BuildFlavor", config.FLAVOR)
        FirebaseCrashlytics.getInstance().setCustomKey("Remote", remote)
        FirebaseCrashlytics.getInstance().setCustomKey("Committed", config.COMMITTED)
        if (hashes.isNotEmpty()) FirebaseCrashlytics.getInstance().setCustomKey("Hash", hashes[0])
        FirebaseCrashlytics.getInstance().setCustomKey("Email", preferences.get(StringKey.MaintenanceIdentification))
    }

    private fun passwordResetCheck(context: Context) {
        val fh = fileListProvider.ensureExtraDirExists()?.findFile("PasswordReset")
        if (fh?.exists() == true) {
            Thread {
                while (activePlugin.activePump.serialNumber().isEmpty()) Thread.sleep(100)
                preferences.put(StringKey.ProtectionMasterPassword, cryptoUtil.hashPassword(activePlugin.activePump.serialNumber()))
                fh.delete()
                exportPasswordDataStore.clearPasswordDataStore(context)
                ToastUtils.okToast(context, context.getString(app.aaps.core.ui.R.string.password_set))
            }.start()
        }
    }

    private fun exportPasswordResetCheck(context: Context) {
        val fh = fileListProvider.ensureExtraDirExists()?.findFile("ExportPasswordReset")
        if (fh?.exists() == true) {
            exportPasswordDataStore.clearPasswordDataStore(context)
            fh.delete()
            ToastUtils.okToast(context, context.getString(app.aaps.core.ui.R.string.datastore_password_cleared))
        }
    }
}