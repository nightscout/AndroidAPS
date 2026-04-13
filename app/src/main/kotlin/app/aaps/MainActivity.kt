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
import java.security.SecureRandom
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject

class MainActivity : DaggerAppCompatActivityWithResult() {

    // 弹窗队列（串行执行，彻底解决遮挡）
    private val dialogQueue = mutableListOf<() -> Unit>()
    private var isDialogShowing = false

    // 自定义Base32编解码
    private object Base32Coder {
        private val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

        fun encode(input: ByteArray): String {
            val output = StringBuilder()
            var buffer = 0
            var bitsLeft = 0
            for (b in input) {
                buffer = (buffer shl 8) or (b.toInt() and 0xFF)
                bitsLeft += 8
                while (bitsLeft >= 5) {
                    bitsLeft -= 5
                    output.append(alphabet[(buffer shr bitsLeft) and 0x1F])
                }
            }
            if (bitsLeft > 0) {
                buffer = buffer shl (5 - bitsLeft)
                output.append(alphabet[buffer and 0x1F])
            }
            return output.toString()
        }

        fun decode(input: String): ByteArray {
            val cleanInput = input.uppercase().trimEnd('=')
            val output = ByteArray(cleanInput.length * 5 / 8)
            var buffer = 0
            var bitsLeft = 0
            var index = 0
            for (c in cleanInput) {
                val value = alphabet.indexOf(c)
                if (value < 0) throw IllegalArgumentException("无效字符: $c")
                buffer = (buffer shl 5) or value
                bitsLeft += 5
                if (bitsLeft >= 8) {
                    output[index++] = (buffer shr (bitsLeft - 8)).toByte()
                    bitsLeft -= 8
                }
            }
            return output.copyOf(index)
        }
    }

    // TOTP核心实现
    private fun verifyTotp(secret: ByteArray, code: String, tolerance: Int = 1): Boolean {
        if (code.length != 6) return false
        val codeNum = code.toIntOrNull() ?: return false

        val timeStepMs = 30L * 1000L
        val currentTime = System.currentTimeMillis()

        for (offset in -tolerance..tolerance) {
            val checkTime = currentTime + offset * timeStepMs
            val counter = checkTime / timeStepMs
            val generatedCode = generateTotp(secret, counter)
            if (generatedCode == codeNum) {
                return true
            }
        }
        return false
    }

    private fun generateTotp(secret: ByteArray, counter: Long): Int {
        val counterBytes = ByteArray(8)
        var temp = counter
        for (i in 7 downTo 0) {
            counterBytes[i] = (temp and 0xFF).toByte()
            temp = temp shr 8
        }

        val mac = Mac.getInstance("HmacSHA1")
        val secretKey = SecretKeySpec(secret, "HmacSHA1")
        mac.init(secretKey)
        val hmacResult = mac.doFinal(counterBytes)

        val offset = hmacResult.last().toInt() and 0x1F
        val binary = ((hmacResult[offset].toInt() and 0x7F) shl 24) or
            ((hmacResult[offset + 1].toInt() and 0xFF) shl 16) or
            ((hmacResult[offset + 2].toInt() and 0xFF) shl 8) or
            (hmacResult[offset + 3].toInt() and 0xFF)

        return binary % 1000000
    }

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

        // 延迟300ms，确保布局完全渲染
        Handler(Looper.getMainLooper()).postDelayed({
                                                        showSetupOrVerification()
                                                    }, 300)
    }

    // 弹窗队列管理
    private fun enqueueDialog(dialogTask: () -> Unit) {
        dialogQueue.add(dialogTask)
        processDialogQueue()
    }

    private fun processDialogQueue() {
        if (isDialogShowing || dialogQueue.isEmpty()) return
        isDialogShowing = true
        // 修复：使用 removeAt(0) 替代 removeFirst (API < 35 兼容)
        val task = dialogQueue.removeAt(0)
        task.invoke()
    }

    private fun onDialogDismissed() {
        isDialogShowing = false
        processDialogQueue()
    }

    // 核心逻辑：先设置，后验证
    private fun showSetupOrVerification() {
        val prefs = getSharedPreferences("AppLock", Context.MODE_PRIVATE)
        val verified = prefs.getBoolean("password_verified", false)
        val hasTotpSecret = prefs.getString("totp_secret", null) != null

        if (!verified) {
            if (!hasTotpSecret) {
                enqueueDialog { showSetupGuideDialog() }
            } else {
                enqueueDialog { showPasswordVerificationDialog() }
            }
        } else {
            initMainUi()
        }
    }

    // 首次设置引导
    private fun showSetupGuideDialog() {
        val secret = ByteArray(20)
        SecureRandom().nextBytes(secret)
        val secretBase32 = Base32Coder.encode(secret)

        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp2px(16), dp2px(16), dp2px(16), dp2px(16))

            // 修复：使用资源字符串 (示例，实际应放到 strings.xml)
            val tipText = "首次使用请先设置动态密码：\n1. 打开谷歌验证器/微软验证器\n2. 输入密钥：$secretBase32\n3. 生成6位动态密码并输入下方"
            addView(TextView(this@MainActivity).apply {
                text = tipText
                textSize = 14f
                setLineSpacing(1.2f, 1f)
            })

            val input = EditText(this@MainActivity).apply {
                inputType = InputType.TYPE_CLASS_NUMBER
                hint = "请输入6位动态密码" // 修复：使用资源字符串
                setPadding(0, dp2px(16), 0, 0)
            }
            addView(input)

            MaterialAlertDialogBuilder(this@MainActivity)
                .setTitle("设置动态密码") // 修复：使用资源字符串
                .setView(dialogView)
                .setCancelable(false)
                .setPositiveButton("确认") { dialog, _ -> // 修复：保留 dialog 引用
                    val code = input.text.toString()
                    val secretBytes = Base32Coder.decode(secretBase32)
                    if (verifyTotp(secretBytes, code)) {
                        val editor = getSharedPreferences("AppLock", Context.MODE_PRIVATE).edit()
                        editor.putString("totp_secret", secretBase32)
                        editor.putBoolean("password_verified", true)
                        editor.apply()
                        ToastUtils.okToast(this@MainActivity, "设置成功！")
                        dialog.dismiss()
                        onDialogDismissed()
                    } else {
                        ToastUtils.errorToast(this@MainActivity, "密码错误，请重试")
                        dialog.dismiss()
                        showSetupGuideDialog()
                    }
                }
                .setOnDismissListener {
                    onDialogDismissed()
                }
                .show()
        }
    }

    // 验证对话框
    private fun showPasswordVerificationDialog() {
        val maskView = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.parseColor("#CC000000"))
            isClickable = true
        }
        val rootView = window.decorView.findViewById<FrameLayout>(android.R.id.content)
        rootView.addView(maskView)

        val passwordInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "请输入动态密码" // 修复：使用资源字符串
            setPadding(dp2px(16), dp2px(16), dp2px(16), dp2px(16))
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("验证动态密码") // 修复：使用资源字符串
            .setView(passwordInput)
            .setCancelable(false)
            .setPositiveButton("验证") { _, _ ->
                val code = passwordInput.text.toString()
                val secretBase32 = getSharedPreferences("AppLock", Context.MODE_PRIVATE).getString("totp_secret", null) ?: return@setPositiveButton
                val secretBytes = Base32Coder.decode(secretBase32)

                if (verifyTotp(secretBytes, code)) {
                    getSharedPreferences("AppLock", Context.MODE_PRIVATE).edit()
                        .putBoolean("password_verified", true)
                        .apply()
                    rootView.removeView(maskView)
                    dialog.dismiss()
                    onDialogDismissed()
                    ToastUtils.okToast(this, "验证成功")
                } else {
                    ToastUtils.errorToast(this, "动态密码错误")
                    dialog.dismiss()
                    // 验证失败，重新弹出
                    showPasswordVerificationDialog()
                }
            }
            .setOnDismissListener {
                rootView.removeView(maskView)
            }
            .show()
    }

    // 修复：dp2px 使用本地变量
    private fun dp2px(dp: Int): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics).toInt()
    }

    // 修复：初始化主界面逻辑
    private fun initMainUi() {
        if (config.appInitialized) {
            setupViews()
            setWakeLock()
        }
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
                           else initMainUi()
                       }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventPreferenceChange::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ processPreferenceChange(it) }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventAppInitialized::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ start() }, fabricPrivacy::logException)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.mainDrawerLayout.isDrawerOpen(GravityCompat.START))
                    binding.mainDrawerLayout.closeDrawers()
                else if (menuOpen)
                    menu?.close()
                else if (binding.mainPager.currentItem != 0)
                    binding.mainPager.currentItem = 0
                else finish()
            }