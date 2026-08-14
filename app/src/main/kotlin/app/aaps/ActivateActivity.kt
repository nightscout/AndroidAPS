package app.aaps

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import app.aaps.core.ui.toast.ToastUtils

/**
 * 全屏设备激活页(仿深色激活 UI)。
 * 唯一验证入口: 首次激活 / 365天过期再验证 都走这里, MainActivity 不再弹窗, 杜绝重复验证。
 * 验证成功 → RESULT_OK 返回 MainActivity → 由其 start() 进入主界面。
 * showKeyOnly=true: 仅展示设备标识+授权状态(从"获取密钥"进入), 不输码、不重置。
 */
class ActivateActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SHOW_KEY_ONLY = "show_key_only"
        private const val PREFS_NAME = "AppLock"
        // 视觉规范(截图像素实测)
        private const val BG = "#121212"
        private const val CARD_BG = "#1F1F1F"
        private const val BORDER = "#3A3A3A"
        private const val TXT_TITLE = "#FFFFFF"
        private const val TXT_SUB = "#C8C8C8"
        private const val TXT_BODY = "#C2C2C2"
        private const val TXT_LABEL = "#9E9E9E"
        private const val TXT_HINT = "#BBBBBB"
        private const val TXT_NOTE = "#B7B7B7"
        private const val TXT_ERR = "#F87171"
        private const val ACCENT = "#4CAF50"
        private const val ACCENT_DARK = "#3D8B40"
    }

    private var showKeyOnly = false
    private var deviceId: String = ""
    private lateinit var idText: TextView
    private lateinit var inputInvite: EditText
    private lateinit var inputPhone: EditText
    private lateinit var btnConfirm: Button
    private lateinit var statusLine: TextView

    private fun dp(px: Int): Int = (px * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor(BG)
        showKeyOnly = intent.getBooleanExtra(EXTRA_SHOW_KEY_ONLY, false)
        setContentView(buildUi())
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (showKeyOnly) { setResult(RESULT_CANCELED); finish() }
                else finish()  // 激活中禁止返回绕过, 直接退出
            }
        })
        prepareSecret()
    }

    /** 生成/读取密钥, 填充设备标识 */
    private fun prepareSecret() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var secret = prefs.getString("totp_secret", null)
        if (!showKeyOnly && secret == null) {
            secret = TotpUtils.generateSecret()
            prefs.edit().putString("totp_secret", secret).apply()
        }
        deviceId = if (secret != null) TotpUtils.deviceIdHex(secret) else ""
        idText.text = if (deviceId.length == 32) {
            deviceId.substring(0, 16) + "\n" + deviceId.substring(16)
        } else {
            "尚未生成设备标识"
        }
        if (showKeyOnly) {
            inputPhone.visibility = View.GONE
            inputInvite.visibility = View.GONE
            btnConfirm.text = "知道了"
            statusLine.text = authStatusText()
            statusLine.visibility = View.VISIBLE
        } else {
            inputPhone.visibility = View.VISIBLE
            inputInvite.visibility = View.VISIBLE
            statusLine.visibility = View.GONE
        }
    }

    private fun authStatusText(): String {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val verified = prefs.getBoolean("password_verified", false)
        val lastTs = prefs.getLong("last_verify_time", 0L)
        if (!verified || lastTs == 0L) return "授权状态：尚未验证"
        val remainMs = TotpUtils.EXPIRE_MS - (System.currentTimeMillis() - lastTs)
        val days = remainMs / (24 * 60 * 60 * 1000)
        return if (remainMs > 0) "授权状态：已验证，剩余 $days 天"
        else "授权状态：已过期 ${-days} 天，请重新验证"
    }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor(BG))
            setPadding(dp(28), dp(48), dp(28), dp(28))
        }
        // 标题
        root.addView(TextView(this).apply {
            text = "欢迎使用AAPS"
            textSize = 22f
            setTextColor(Color.parseColor(TXT_TITLE))
            typeface = Typeface.DEFAULT_BOLD
        })
        // 副标题
        root.addView(TextView(this).apply {
            text = "请输入邀请码完成设备激活"
            textSize = 14f
            setTextColor(Color.parseColor(TXT_SUB))
            setPadding(0, dp(6), 0, dp(24))
        })
        // 设备标识标签
        root.addView(TextView(this).apply {
            text = "设备标识"
            textSize = 13f
            setTextColor(Color.parseColor(TXT_LABEL))
        })
        // 设备标识 + 复制按钮
        val idRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }
        idText = TextView(this).apply {
            textSize = 16f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor(TXT_TITLE))
            setPadding(0, 0, dp(12), 0)
        }
        idRow.addView(idText, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        idRow.addView(Button(this).apply {
            text = "复制"
            textSize = 13f
            setTextColor(Color.parseColor(TXT_BODY))
            background = rounded(dp(8), "#2A2A2A", BORDER, 1)
            setOnClickListener {
                if (deviceId.isNotEmpty()) {
                    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("deviceId", deviceId))
                    ToastUtils.okToast(this@ActivateActivity, "设备标识已复制")
                }
            }
        })
        root.addView(idRow)
        // 操作提示
        root.addView(TextView(this).apply {
            text = "请复制上方设备ID发送给管理员获取邀请码"
            textSize = 13f
            setTextColor(Color.parseColor(TXT_BODY))
            setPadding(0, 0, 0, dp(20))
        })
        // 手机号(选填)
        root.addView(TextView(this).apply {
            text = "手机号(选填)"
            textSize = 13f
            setTextColor(Color.parseColor(TXT_LABEL))
            setPadding(0, 0, 0, dp(6))
        })
        inputPhone = EditText(this).apply {
            hint = "用于管理员登记(选填)"
            setHintTextColor(Color.parseColor(TXT_HINT))
            setTextColor(Color.parseColor(TXT_TITLE))
            inputType = InputType.TYPE_CLASS_PHONE
            background = rounded(dp(10), CARD_BG, BORDER, 1)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            maxLines = 1
        }
        root.addView(inputPhone, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)).apply {
            bottomMargin = dp(18)
        })
        // 邀请码标签
        root.addView(TextView(this).apply {
            text = "邀请码"
            textSize = 13f
            setTextColor(Color.parseColor(TXT_LABEL))
            setPadding(0, 0, 0, dp(6))
        })
        inputInvite = EditText(this).apply {
            hint = "请输入8位邀请码"
            setHintTextColor(Color.parseColor(TXT_HINT))
            setTextColor(Color.parseColor(TXT_TITLE))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            background = rounded(dp(10), CARD_BG, BORDER, 1)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            maxLines = 1
        }
        root.addView(inputInvite, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)).apply {
            bottomMargin = dp(6)
        })
        // 区分大小写提示
        root.addView(TextView(this).apply {
            text = "邀请码区分大小写"
            textSize = 11f
            setTextColor(Color.parseColor(TXT_NOTE))
            setPadding(0, 0, 0, dp(24))
        })
        // 状态行(仅 showKeyOnly 显示)
        statusLine = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.parseColor(TXT_NOTE))
            setPadding(0, 0, 0, dp(12))
            visibility = View.GONE
        }
        root.addView(statusLine)
        // 确认按钮
        btnConfirm = Button(this).apply {
            text = if (showKeyOnly) "知道了" else "确认"
            textSize = 15f
            setTextColor(Color.WHITE)
            background = rounded(dp(12), ACCENT, ACCENT, 0)
            setOnClickListener { onConfirm() }
        }
        root.addView(btnConfirm, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52)))
        // 底部链接: 获取密钥 / 重置
        val linkRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, 0)
        }
        linkRow.addView(TextView(this).apply {
            text = if (showKeyOnly) "重新验证" else "获取密钥"
            textSize = 13f
            setTextColor(Color.parseColor("#5B8DEF"))
            setPadding(dp(8), dp(4), dp(8), dp(4))
            setOnClickListener {
                if (showKeyOnly) {
                    // 返回上一实例(激活页), 不做新跳转
                    finish()
                } else {
                    startActivity(Intent(this@ActivateActivity, ActivateActivity::class.java)
                        .putExtra(EXTRA_SHOW_KEY_ONLY, true))
                }
            }
        })
        root.addView(linkRow)

        val scroll = ScrollView(this).apply { addView(root) }
        return scroll
    }

    private fun rounded(radius: Int, fill: String, stroke: String, strokeW: Int): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = radius.toFloat()
            setColor(Color.parseColor(fill))
            if (strokeW > 0) setStroke(strokeW, Color.parseColor(stroke))
        }

    private fun onConfirm() {
        if (showKeyOnly) { setResult(RESULT_CANCELED); finish(); return }
        val code = inputInvite.text.toString().trim()
        if (code.isEmpty()) { ToastUtils.errorToast(this, "请输入8位邀请码"); return }
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val secret = prefs.getString("totp_secret", null)
        if (secret == null) { ToastUtils.errorToast(this, "设备标识异常，请重置"); return }
        if (TotpUtils.verifyTotp(secret, code)) {
            val phone = inputPhone.text.toString().trim()
            if (phone.isNotEmpty()) prefs.edit().putString("phone", phone).apply()
            prefs.edit()
                .putBoolean("password_verified", true)
                .putLong("last_verify_time", System.currentTimeMillis())
                .apply()
            ToastUtils.okToast(this, "授权码输入成功！365天后需要重新验证")
            setResult(RESULT_OK)
            finish()
        } else {
            ToastUtils.errorToast(this, "邀请码错误，请核对后重试")
        }
    }
}
