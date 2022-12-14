package info.nightscout.pump.diaconn.pumplog;

import android.annotation.SuppressLint;

import org.apache.commons.lang3.RegExUtils;
import org.apache.commons.lang3.StringUtils;

import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/*
 * 디아콘 G8 펌프 로그 유틸리티 클래스
 */
@SuppressWarnings({"CommentedOutCode", "SpellCheckingInspection"})
public class PumpLogUtil {
	/*
	 * 바이트버퍼에서 4바이트 날짜를 구한다.
	 * @param buffer 바이트버퍼
	 * @return GMT 날짜 문자열
	 */
    @SuppressLint("SimpleDateFormat")
	public static String getDttm(ByteBuffer buffer) {
		byte b0 = buffer.get();
		byte b1 = buffer.get();
		byte b2 = buffer.get();
		byte b3 = buffer.get();
		long pumpTime = Long.parseLong(String.format("%02x%02x%02x%02x", b3, b2, b1, b0), 16);
		long epochTime = new Date(0).getTime(); // 1970-01-01
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		sdf.setTimeZone(TimeZone.getTimeZone("UCT"));
		return sdf.format(new Date(epochTime + pumpTime * 1000));
	}

	/*
	 * 바이트버퍼에서 4바이트 날짜를 구한다.
	 * @param data 로그데이터
	 * @return GMT 날짜 문자열
	 */
    @SuppressLint("SimpleDateFormat")
	public static String getDttm(String data) {
		byte[] bytes = PumpLogUtil.hexStringToByteArray(data);
		byte b0 = bytes[0];
		byte b1 = bytes[1];
		byte b2 = bytes[2];
		byte b3 = bytes[3];
		long pumpTime = Long.parseLong(String.format("%02x%02x%02x%02x", b3, b2, b1, b0), 16);
		long epochTime = new Date(0).getTime(); // 1970-01-01
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
		return sdf.format(new Date(epochTime + pumpTime * 1000));
	}

	/*
	 * 바이트버퍼에서 1바이트를 구한다.
	 * @param buffer 바이트버퍼
	 * @return byte
	 */
	public static byte getByte(ByteBuffer buffer) {
		return buffer.get();
	}

	/*
	 * 바이트버퍼에서 2바이트를 구한다.
	 * @param buffer 바이트버퍼
	 * @return short
	 */
	public static short getShort(ByteBuffer buffer) {
		return buffer.getShort();
	}

	/*
	 * 바이트버퍼에서 4바이트를 구한다.
	 * @param buffer 바이트버퍼
	 * @return int
	 */
    /*
	public static int getInt(ByteBuffer buffer) {
		return buffer.getInt();
	}
	*/

	/*
	 * 로그데이터에서 로그 타입 바이트를 구한다.
	 * @param data 로그데이터
	 * @return byte
	 */
	public static byte getType(String data) {
		byte[] bytes = hexStringToByteArray(data);
		return getType(bytes[4]);
	}

	/*
	 * 로그데이터에서 로그 종류 바이트를 구한다.
	 * @param data 로그데이터
	 * @return byte
	 */
	public static byte getKind(String data) {
		byte[] bytes = hexStringToByteArray(data);
		//System.out.println("byte=" + Arrays.toString(bytes));
		return getKind(bytes[4]);
	}

	/*
	 * 바이트 데이터에서 로그 타입 부분을 추출 한다.
	 * @param b 바이트
	 * @return byte
	 */
	public static byte getType(byte b) {
		return (byte) ((b >> 6) & 0b00000011); // 상위 2비트 추출
	}

	/*
	 * 바이트 데이터에서 로그 종류 부분을 추출 한다.
	 * @param b 바이트
	 * @return byte
	 */
	public static byte getKind(byte b) {
		return (byte) (b & 0b00111111); // 하위 6비트 추출
	}

	/*
	 * 16진수 표기 문자열을 바이트 배열로 변환한다.
	 * @param s 16진수 표기 문자열
	 * @return byte[]
	 */
	public static byte[] hexStringToByteArray(String s) {
		int len = s.length();
		byte[] data = new byte[len / 2];
		for (int i = 0; i < len; i += 2) {
			data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i + 1), 16));
		}
		return data;
	}

	/*
	 * 펌프 시간을 날짜 타입으로 변환한다.(펌프에서 GMT기준으로 초를 구하므로, Date 변환시 GMT 기준으로 구해야 한다.
	 * @param data 1970.1.1이후 경과한 초
	 * @return 날짜(GMT기준)
	 */
    /*
	public static Date pumpTimeToGMTDate(Integer data) {
		long epochTime = new Date(0).getTime(); // 1970-01-01
		long pumpTime = data.longValue() * 1000; // 초를 밀리초 단위로 변환
		int timeZoneOffset = TimeZone.getDefault().getRawOffset(); // GMT와 로컬 타임존 사이의 차이
		return new Date(epochTime + pumpTime - timeZoneOffset);
	}
    */

    /*
     * 펌프 버전이 해당 버전보다 크거나 같은지 여부 확인(새로운 기능이 추가된 버전을 체크하기 위함)
     */
    public static boolean isPumpVersionGe(String pump_version, int major, int minor) {
        String version = RegExUtils.replaceAll(pump_version, "[^\\d\\.]", "");
        int pump_major = Integer.parseInt(StringUtils.split(version, ".")[0]);
        int pump_minor = Integer.parseInt(StringUtils.split(version, ".")[1]);
        if (pump_major > major) { // 메이저 버전이 클 때
            return true;
        } else if (pump_major < major) { // 메이저 버전이 작을 때
            return false;
        } else { // 메이저 버전이 같다면 마이너 버전이 같거나 클 때
            return pump_minor >= minor;
        }
    }
}