package app.aaps

/**
 * 授权锁工具(独立文件, MainActivity 与 ActivateActivity 共用)
 * 规格: 设备标识=16字节随机的32位hex; 邀请码=HMAC-SHA1/30s/8位Base58(去0O1Il)/容差1
 * 与面板 local_proxy._totp_code 算法完全一致(两端可互通)。
 */
object TotpUtils {
    const val EXPIRE_MS = 365L * 24 * 60 * 60 * 1000  // 365天授权周期
    private const val DEFAULT_SECRET_SIZE = 16
    private const val DEFAULT_CODE_DIGITS = 8
    private const val DEFAULT_TIME_STEP = 30L
    private const val DEFAULT_TOLERANCE = 1
    // Base58 邀请码字符集: 去易混淆 0/O/1/I/l (与面板 local_proxy._totp_code 保持一致)
    private val BASE58_CHARS = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    private val HEX_CHARS = "0123456789abcdef".toCharArray()

    private val BASE32_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ2345678".toCharArray()
    private val BASE32_MAP = BASE32_CHARS.withIndex().associate { it.value to it.index }.toMap()

    fun generateSecret(): String {
        val random = java.security.SecureRandom()
        val secret = ByteArray(DEFAULT_SECRET_SIZE)
        random.nextBytes(secret)
        return encodeBase32(secret)
    }

    /** 设备标识: 内部 Base32 密钥同字节的 32 位 hex (患者截图发给管理员, 管理员凭它出邀请码) */
    fun deviceIdHex(secretBase32: String): String {
        val sb = StringBuilder(DEFAULT_SECRET_SIZE * 2)
        for (b in decodeBase32(secretBase32)) {
            sb.append(HEX_CHARS[(b.toInt() shr 4) and 0xF])
            sb.append(HEX_CHARS[b.toInt() and 0xF])
        }
        return sb.toString()
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

        // 8 位 Base58 邀请码: 取 hash 前 8 字节拼 64bit, 连除 58 映射 (58^8≈1.28e14, 64bit 熵充足; 与面板同算法)
        var v = 0L
        for (i in 0 until 8) v = (v shl 8) or (hash[i].toLong() and 0xFF)
        val sb = StringBuilder(DEFAULT_CODE_DIGITS)
        repeat(DEFAULT_CODE_DIGITS) {
            sb.append(BASE58_CHARS[(v % 58).toInt()])
            v /= 58
        }
        return sb.toString()
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
