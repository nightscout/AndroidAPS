package info.nightscout.androidaps.plugins.PumpDanaRS.comm;

import android.annotation.TargetApi;
import android.os.Build;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import com.cozmo.danar.util.BleCommandUtil;

public class DanaRS_Packet {
	protected static final int TYPE_START = 0;
	protected static final int OPCODE_START = 1;
	protected static final int DATA_START = 2;

	private boolean received;
	protected int type = BleCommandUtil.DANAR_PACKET__TYPE_RESPONSE; // most of the messages, should be changed for others
	protected int opCode;

	public DanaRS_Packet() {
		received = false;
	}

	public void setReceived() {
		received = true;
	}

	public boolean isReceived() {
		return received;
	}

	public int getType() {
		return type;
	}

	public int getOpCode() {
		return opCode;
	}

	public int getCommand() {
		return ((type & 0xFF) << 8) + (opCode & 0xFF);
	}

	public byte[] getRequestParams() {
		return null;
	};

	// STATIC FUNCTIONS

    public static int getCommand(byte[] data) {
        int type = byteArrayToInt(getBytes(data, TYPE_START, 1));
        int opCode = byteArrayToInt(getBytes(data, OPCODE_START, 1));
        return ((type & 0xFF) << 8) + (opCode & 0xFF);
    }

    public void handleMessage(byte[] data) {
    }

	public String getFriendlyName() {
        return "UNKNOWN_PACKET";
    }

/*
	public static DanaRS_Packet parseResponse(byte[] data) {
		DanaRS_Packet ret = null;
		try {

			int type = byteArrayToInt(getBytes(data, TYPE_START, 1));
			int tOpCode = byteArrayToInt(getBytes(data, OPCODE_START, 1));
			if (type == BleCommandUtil.DANAR_PACKET__TYPE_NOTIFY) {
				switch (tOpCode) {
					// Notify
					case BleCommandUtil.DANAR_PACKET__OPCODE_NOTIFY__DELIVERY_COMPLETE:
						ret = new DanaRS_Packet_Notify_Delivery_Complete();
						break;
					case BleCommandUtil.DANAR_PACKET__OPCODE_NOTIFY__DELIVERY_RATE_DISPLAY:
						ret = new DanaRS_Packet_Notify_Delivery_Rate_Display();
						break;
					case BleCommandUtil.DANAR_PACKET__OPCODE_NOTIFY__ALARM:
						ret = new DanaRS_Packet_Notify_Alarm();
						break;
					case BleCommandUtil.DANAR_PACKET__OPCODE_NOTIFY__MISSED_BOLUS_ALARM:
						ret = new DanaRS_Packet_Notify_Missed_Bolus_Alarm();
						break;
				}
			} else if (type == BleCommandUtil.DANAR_PACKET__TYPE_RESPONSE) {
				switch (tOpCode) {
					// Init
					case BleCommandUtil.DANAR_PACKET__OPCODE_REVIEW__INITIAL_SCREEN_INFORMATION:
						ret = new DanaRS_Packet_General_Initial_Screen_Information();
						break;
					case BleCommandUtil.DANAR_PACKET__OPCODE_REVIEW__DELIVERY_STATUS:
						ret = new DanaRS_Packet_General_Delivery_Status();
						break;
					case BleCommandUtil.DANAR_PACKET__OPCODE_REVIEW__GET_PASSWORD:
						ret = new DanaRS_Packet_General_Get_Password();
						break;

					// Review
					case BleCommandUtil.DANAR_PACKET__OPCODE_REVIEW__BOLUS_AVG:
						ret = new DanaRS_Packet_Review_Bolus_Avg();
						break;
					case BleCommandUtil.DANAR_PACKET__OPCODE_REVIEW__BOLUS:
						ret = new DanaRS_Packet_History_Bolus();
						break;
					case BleCommandUtil.DANAR_PACKET__OPCODE_REVIEW__DAILY:
						ret = new DanaRS_Packet_History_Daily();
						break;
					case BleCommandUtil.DANAR_PACKET__OPCODE_REVIEW__PRIME:
						ret = new DanaRS_Packet_History_Prime();
						break;
					case BleCommandUtil.DANAR_PACKET__OPCODE_REVIEW__REFILL:
						ret = new DanaRS_Packet_History_Refill();
						break;
					case BleCommandUtil.DANAR_PACKET__OPCODE_REVIEW__BLOOD_GLUCOSE:
						ret = new DanaRS_Packet_History_Blood_Glucose();
						break;
					case BleCommandUtil.DANAR_PACKET__OPCODE_REVIEW__CARBOHYDRATE:
						ret = new DanaRS_Packet_History_Carbohydrate();
						break;
					case BleCommandUtil.DANAR_PACKET__OPCODE_REVIEW__TEMPORARY:
						ret = new DanaRS_Packet_History_Temporary();
						break;
					case BleCommandUtil.DANAR_PACKET__OPCODE_REVIEW__SUSPEND:
						ret = new DanaRS_Packet_History_Suspend();
						break;
					case BleCommandUtil.DANAR_PACKET__OPCODE_REVIEW__ALARM:
						ret = new DanaRS_Packet_History_Alarm();
						break;
					case BleCommandUtil.DANAR_PACKET__OPCODE_REVIEW__BASAL:
						ret = new DanaRS_Packet_History_Basal();
						break;
					case BleCommandUtil.DANAR_PACKET__OPCODE_REVIEW__ALL_HISTORY:
						ret = new DanaRS_Packet_History_All_History();
						break;
					case BleCommandUtil.DANAR_PACKET__OPCODE_REVIEW__GET_SHIPPING_INFORMATION:
						ret = new DanaRS_Packet_General_Get_Shipping_Information();
						break;
					case BleCommandUtil.DANAR_PACKET__OPCODE_REVIEW__GET_PUMP_CHECK:
						ret = new DanaRS_Packet_General_Get_Pump_Check();
						break;
					case BleCommandUtil.DANAR_PACKET__OPCODE_REVIEW__GET_USER_TIME_CHANGE_FLAG:
						ret = new DanaRS_Packet_General_Get_User_Time_Change_Flag();
						break;
					case BleCommandUtil.DANAR_PACKET__OPCODE_REVIEW__SET_USER_TIME_CHANGE_FLAG_CLEAR:
						ret = new DanaRS_Packet_General_Set_User_Time_Change_Flag_Clear();
						break;
					case BleCommandUtil.DANAR_PACKET__OPCODE_REVIEW__GET_MORE_INFORMATION:
						ret = new DanaRS_Packet_General_Get_More_Information();
						break;
					case BleCommandUtil.DANAR_PACKET__OPCODE_REVIEW__SET_HISTORY_UPLOAD_MODE:
						ret = new DanaRS_Packet_General_Set_History_Upload_Mode();
						break;
					case BleCommandUtil.DANAR_PACKET__OPCODE_REVIEW__GET_TODAY_DELIVERY_TOTAL:
						ret = new DanaRS_Packet_General_Get_Today_Delivery_Total();
						break;

					// Bolus
					case BleCommandUtil.DANAR_PACKET__OPCODE_BOLUS__GET_STEP_BOLUS_INFORMATION:
						ret = new DanaRS_Packet_Bolus_Get_Step_Bolus_Information();
						break;
					case BleCommandUtil.DANAR_PACKET__OPCODE_BOLUS__GET_EXTENDED_BOLUS_STATE:
						ret = new DanaRS_Packet_Bolus_Get_Extended_Bolus_State();
						break;
					case BleCommandUtil.DANAR_PACKET__OPCODE_BOLUS__GET_EXTENDED_BOLUS:
						ret = new DanaRS_Packet_Bolus_Get_Extended_Bolus();
						break;
					case BleCommandUtil.DANAR_PACKET__OPCODE_BOLUS__GET_DUAL_BOLUS:
						ret = new DanaRS_Packet_Bolus_Get_Dual_Bolus();
						break;
					case BleCommandUtil.DANAR_PACKET__OPCODE_BOLUS__SET_STEP_BOLUS_STOP:
						ret = new DanaRS_Packet_Bolus_Set_Step_Bolus_Stop();
						break;
					case BleCommandUtil.DANAR_PACKET__OPCODE_BOLUS__GET_CARBOHYDRATE_CALCULATION_INFORMATION:
						ret = new DanaRS_Packet_Bolus_Get_Carbohydrate_Calculation_Information();
						break;
					case BleCommandUtil.DANAR_PACKET__OPCODE_BOLUS__GET_EXTENDED_MENU_OPTION_STATE:
						ret = new DanaRS_Packet_Bolus_Get_Extended_Menu_Option_State();
						break;
					case BleCommandUtil.DANAR_PACKET__OPCODE_BOLUS__SET_EXTENDED_BOLUS:
						ret = new DanaRS_Packet_Bolus_Set_Extended_Bolus();
						break;
					case BleCommandUtil.DANAR_PACKET__OPCODE_BOLUS__SET_DUAL_BOLUS:
						ret = new DanaRS_Packet_Bolus_Set_Dual_Bolus();
						break;
					case BleCommandUtil.DANAR_PACKET__OPCODE_BOLUS__SET_EXTENDED_BOLUS_CANCEL:
						ret = new DanaRS_Packet_Bolus_Set_Extended_Bolus_Cancel();
						break;
					case BleCommandUtil.DANAR_PACKET__OPCODE_BOLUS__SET_STEP_BOLUS_START:
						ret = new DanaRS_Packet_Bolus_Set_Step_Bolus_Start();
						break;
					case BleCommandUtil.DANAR_PACKET__OPCODE_BOLUS__GET_CALCULATION_INFORMATION:
						ret = new DanaRS_Packet_Bolus_Get_Calculation_Information();
						break;
					case BleCommandUtil.DANAR_PACKET__OPCODE_BOLUS__GET_BOLUS_RATE:
						ret = new DanaRS_Packet_Bolus_Get_Initial_Bolus();
						break;
					case BleCommandUtil.DANAR_PACKET__OPCODE_BOLUS__SET_BOLUS_RATE:
						ret = new DanaRS_Packet_Bolus_Set_Initial_Bolus();
						break;
					case BleCommandUtil.DANAR_PACKET__OPCODE_BOLUS__GET_CIR_CF_ARRAY:
						ret = new DanaRS_Packet_Bolus_Get_CIR_CF_Array();
						break;
					case BleCommandUtil.DANAR_PACKET__OPCODE_BOLUS__SET_CIR_CF_ARRAY:
						ret = new DanaRS_Packet_Bolus_Set_CIR_CF_Array();
						break;
					case BleCommandUtil.DANAR_PACKET__OPCODE_BOLUS__GET_BOLUS_OPTION:
						ret = new DanaRS_Packet_Bolus_Get_Bolus_Option();
						break;
					case BleCommandUtil.DANAR_PACKET__OPCODE_BOLUS__SET_BOLUS_OPTION:
						ret = new DanaRS_Packet_Bolus_Set_Bolus_Option();
						break;

					// Basal
					case BleCommandUtil.DANAR_PACKET__OPCODE_BASAL__SET_TEMPORARY_BASAL:
						ret = new DanaRS_Packet_Basal_Set_Temporary_Basal();
						break;
					case BleCommandUtil.DANAR_PACKET__OPCODE_BASAL__TEMPORARY_BASAL_STATE:
						ret = new DanaRS_Packet_Basal_Get_Temporary_Basal_State();
						break;
					case BleCommandUtil.DANAR_PACKET__OPCODE_BASAL__CANCEL_TEMPORARY_BASAL:
						ret = new DanaRS_Packet_Basal_Set_Cancel_Temporary_Basal();
						break;
					case BleCommandUtil.DANAR_PACKET__OPCODE_BASAL__GET_PROFILE_NUMBER:
						ret = new DanaRS_Packet_Basal_Get_Profile_Number();
						break;
					case BleCommandUtil.DANAR_PACKET__OPCODE_BASAL__SET_PROFILE_NUMBER:
						ret = new DanaRS_Packet_Basal_Set_Profile_Number();
						break;
					case BleCommandUtil.DANAR_PACKET__OPCODE_BASAL__GET_PROFILE_BASAL_RATE:
						ret = new DanaRS_Packet_Basal_Get_Profile_Basal_Rate();
						break;
					case BleCommandUtil.DANAR_PACKET__OPCODE_BASAL__SET_PROFILE_BASAL_RATE:
						ret = new DanaRS_Packet_Basal_Set_Profile_Basal_Rate();
						break;
					case BleCommandUtil.DANAR_PACKET__OPCODE_BASAL__GET_BASAL_RATE:
						ret = new DanaRS_Packet_Basal_Get_Basal_Rate();
						break;
					case BleCommandUtil.DANAR_PACKET__OPCODE_BASAL__SET_BASAL_RATE:
						ret = new DanaRS_Packet_Basal_Set_Basal_Rate();
						break;
					case BleCommandUtil.DANAR_PACKET__OPCODE_BASAL__SET_SUSPEND_ON:
						ret = new DanaRS_Packet_Basal_Set_Suspend_On();
						break;
					case BleCommandUtil.DANAR_PACKET__OPCODE_BASAL__SET_SUSPEND_OFF:
						ret = new DanaRS_Packet_Basal_Set_Suspend_Off();
						break;

					// Option
					case BleCommandUtil.DANAR_PACKET__OPCODE_OPTION__GET_PUMP_TIME:
						ret = new DanaRS_Packet_Option_Get_Pump_Time();
						break;
					case BleCommandUtil.DANAR_PACKET__OPCODE_OPTION__SET_PUMP_TIME:
						ret = new DanaRS_Packet_Option_Set_Pump_Time();
						break;
					case BleCommandUtil.DANAR_PACKET__OPCODE_OPTION__GET_USER_OPTION:
						ret = new DanaRS_Packet_Option_Get_User_Option();
						break;
					case BleCommandUtil.DANAR_PACKET__OPCODE_OPTION__SET_USER_OPTION:
						ret = new DanaRS_Packet_Option_Set_User_Option();
						break;

					// Etc
					case BleCommandUtil.DANAR_PACKET__OPCODE_ETC__SET_HISTORY_SAVE:
						ret = new DanaRS_Packet_Etc_Set_History_Save();
						break;
					case BleCommandUtil.DANAR_PACKET__OPCODE_ETC__KEEP_CONNECTION:
						ret = new DanaRS_Packet_Etc_Keep_Connection();
						break;
				}
			}

		} catch (Exception e) {
			ret = null;
		}

		return ret;
	}
*/
	protected static byte[] getBytes(byte[] data, int srcStart, int srcLength) {
		try {
			byte[] ret = new byte[srcLength];

			System.arraycopy(data, srcStart, ret, 0, srcLength);

			return ret;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	protected static int byteArrayToInt(byte[] b) {
		int ret;

		switch (b.length) {
			case 1:
				ret = b[0] & 0x000000FF;
				break;
			case 2:
				ret = ((b[1] & 0x000000FF) << 8) + (b[0] & 0x000000FF);
				break;
			case 3:
				ret = ((b[2] & 0x000000FF) << 16) + ((b[1] & 0x000000FF) << 8) + (b[0] & 0x000000FF);
				break;
			case 4:
				ret = ((b[3] & 0x000000FF) << 24) + ((b[2] & 0x000000FF) << 16) + ((b[1] & 0x000000FF) << 8) + (b[0] & 0x000000FF);
				break;
			default:
				ret = -1;
				break;
		}
		return ret;
	}

	@TargetApi(Build.VERSION_CODES.KITKAT)
	public static String stringFromBuff(byte[] buff, int offset, int length) {
		byte[] strbuff = new byte[length];
		System.arraycopy(buff, offset, strbuff, 0, length);
		return new String(strbuff, StandardCharsets.UTF_8);
	}

	public static Date dateFromBuff(byte[] buff, int offset) {
		Date date =
				new Date(
						100 + byteArrayToInt(getBytes(buff, offset, 1)),
						byteArrayToInt(getBytes(buff, offset + 1, 1)) - 1,
								byteArrayToInt(getBytes(buff, offset + 2, 1))
				);
		return date;
	}

	@TargetApi(Build.VERSION_CODES.KITKAT)
	public static String asciiStringFromBuff(byte[] buff, int offset, int length) {
		byte[] strbuff = new byte[length];
		System.arraycopy(buff, offset, strbuff, 0, length);
		for (int pos = 0; pos < length; pos++)
			strbuff[pos] += 65; // "A"
		return new String(strbuff, StandardCharsets.UTF_8);
	}

	public static String toHexString(byte[] buff) {
        if (buff == null)
            return "";

		StringBuffer sb = new StringBuffer();

		int count = 0;
		for (byte element : buff) {
			sb.append(String.format("%02X ", element));
			if (++count % 4 == 0) sb.append(" ");
		}

		return sb.toString();
	}

	final private static char[] hexArray = "0123456789ABCDEF".toCharArray();

	public static String bytesToHex(byte[] bytes) {
		char[] hexChars = new char[bytes.length * 2];
		for (int j = 0; j < bytes.length; j++) {
			int v = bytes[j] & 0xFF;
			hexChars[j * 2] = hexArray[v >>> 4];
			hexChars[j * 2 + 1] = hexArray[v & 0x0F];
		}
		return new String(hexChars);
	}

	public static byte[] hexToBytes(String s) {
		int len = s.length();
		byte[] data = new byte[len / 2];
		for (int i = 0; i < len; i += 2) {
			data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
					+ Character.digit(s.charAt(i + 1), 16));
		}
		return data;
	}

	public static int ByteToInt(byte b) {
		return b & 0x000000FF;
	}

}
