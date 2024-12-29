package app.aaps.pump.diaconn.pumplog

import android.annotation.SuppressLint
import org.apache.commons.lang3.RegExUtils
import org.apache.commons.lang3.StringUtils
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone

/*
 * 디아콘 G8 펌프 로그 유틸리티 클래스
 */
@Suppress("SpellCheckingInspection")
object PumpLogUtil {

    /**
     * 바이트버퍼에서 4바이트 날짜를 구한다.
     * @param buffer 바이트버퍼
     * @return GMT 날짜 문자열
     */
    @SuppressLint("SimpleDateFormat")
    fun getDttm(buffer: ByteBuffer): String {
        val b0 = buffer.get()
        val b1 = buffer.get()
        val b2 = buffer.get()
        val b3 = buffer.get()
        val pumpTime = String.format("%02x%02x%02x%02x", b3, b2, b1, b0).toLong(16)
        val epochTime = Date(0).time // 1970-01-01
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        sdf.timeZone = TimeZone.getTimeZone("UCT")
        return sdf.format(Date(epochTime + pumpTime * 1000))
    }

    /**
     * 바이트버퍼에서 4바이트 날짜를 구한다.
     * @param data 로그데이터
     * @return GMT 날짜 문자열
     */
    @SuppressLint("SimpleDateFormat")
    fun getDttm(data: String): String {
        val bytes: ByteArray = hexStringToByteArray(data)
        val b0 = bytes[0]
        val b1 = bytes[1]
        val b2 = bytes[2]
        val b3 = bytes[3]
        val pumpTime = String.format("%02x%02x%02x%02x", b3, b2, b1, b0).toLong(16)
        val epochTime = Date(0).time // 1970-01-01
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date(epochTime + pumpTime * 1000))
    }

    /**
     * 바이트버퍼에서 1바이트를 구한다.
     * @param buffer 바이트버퍼
     * @return byte
     */
    fun getByte(buffer: ByteBuffer): Byte {
        return buffer.get()
    }

    /**
     * 바이트버퍼에서 2바이트를 구한다.
     * @param buffer 바이트버퍼
     * @return short
     */
    fun getShort(buffer: ByteBuffer): Short {
        return buffer.getShort()
    }

    /**
     * 로그데이터에서 로그 타입 바이트를 구한다.
     * @param data 로그데이터
     * @return byte
     */
    fun getType(data: String): Byte {
        val bytes: ByteArray = hexStringToByteArray(data)
        return getType(bytes[4])
    }

    /**
     * 로그데이터에서 로그 종류 바이트를 구한다.
     * @param data 로그데이터
     * @return byte
     */
    fun getKind(data: String): Byte {
        val bytes: ByteArray = hexStringToByteArray(data)
        //System.out.println("byte=" + Arrays.toString(bytes));
        return getKind(bytes[4])
    }

    /**
     * 바이트 데이터에서 로그 타입 부분을 추출 한다.
     * @param b 바이트
     * @return byte
     */
    fun getType(b: Byte): Byte {
        return ((b.toInt() shr 6) and 3).toByte() // 상위 2비트 추출
    }

    /**
     * 바이트 데이터에서 로그 종류 부분을 추출 한다.
     * @param b 바이트
     * @return byte
     */
    fun getKind(b: Byte): Byte {
        return (b.toInt() and 63).toByte() // 하위 6비트 추출
    }

    /**
     * 16진수 표기 문자열을 바이트 배열로 변환한다.
     * @param s 16진수 표기 문자열
     * @return byte[]
     */
    fun hexStringToByteArray(s: String): ByteArray {
        val len = s.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((s[i].digitToInt(16) shl 4) + s[i + 1].digitToInt(16)).toByte()
            i += 2
        }
        return data
    }

    /**
     * 펌프 버전이 해당 버전보다 크거나 같은지 여부 확인(새로운 기능이 추가된 버전을 체크하기 위함)
     */
    fun isPumpVersionGe(pumpVersion: String?, major: Int, minor: Int): Boolean {
        val version = RegExUtils.replaceAll(pumpVersion, "[^\\d\\.]", "")
        val pumpMajor = StringUtils.split(version, ".")[0].toInt()
        val pumpMinor = StringUtils.split(version, ".")[1].toInt()
        return if (pumpMajor > major) { // 메이저 버전이 클 때
            true
        } else if (pumpMajor < major) { // 메이저 버전이 작을 때
            false
        } else { // 메이저 버전이 같다면 마이너 버전이 같거나 클 때
            pumpMinor >= minor
        }
    }
}