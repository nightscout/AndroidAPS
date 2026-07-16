package app.aaps.pump.aaruy.common.util;

import android.annotation.SuppressLint;
import android.content.res.Resources;
import android.util.Log;
import android.util.TypedValue;

import java.math.BigDecimal;

public class AaruyNumberUtils {

    public static int doubleToInt(double doubleValue) {
        return new BigDecimal(getDotTwoString(doubleValue)).setScale(0, BigDecimal.ROUND_HALF_UP).intValue();
    }


    /**
     * 计算两个double类型相除后的保留scale位小数
     *
     * @param v1
     * @param v2
     * @param scale
     * @return
     */
    public static double division(double v1, double v2, int scale) {
        if (scale < 0) {
            throw new IllegalArgumentException("The scale must be a positive integer or zero");
        }
        if (v2 == 0) {
            throw new IllegalArgumentException("The v2 must not be zero");
        }
        BigDecimal b1 = new BigDecimal(Double.toString(v1));
        BigDecimal b2 = new BigDecimal(Double.toString(v2));
        return b1.divide(b2, scale, BigDecimal.ROUND_HALF_UP).doubleValue();
    }

    /**
     * 两个double数相乘
     *
     * @param v1
     * @param v2
     * @return
     */
    public static double multiply(double v1, double v2) {
        BigDecimal b1 = new BigDecimal(Double.toString(v1));
        BigDecimal b2 = new BigDecimal(Double.toString(v2));
        return b1.multiply(b2).doubleValue();
    }

    /**
     * 两个double值相减
     *
     * @param v1
     * @param v2
     * @return
     */
    public static double subtract(double v1, double v2) {
        BigDecimal b1 = new BigDecimal(Double.toString(v1));
        BigDecimal b2 = new BigDecimal(Double.toString(v2));
        return b1.subtract(b2).doubleValue();
    }

    /**
     * 两个double值相加
     *
     * @param v1
     * @param v2
     * @return
     */
    public static double add(double v1, double v2) {
        BigDecimal b1 = new BigDecimal(Double.toString(v1));
        BigDecimal b2 = new BigDecimal(Double.toString(v2));
        return b1.add(b2).doubleValue();
    }

    //获取带两位小数的double数
    @SuppressLint("DefaultLocale")
    public static double getDotTwoDouble(double num) {
        BigDecimal two = new BigDecimal(String.valueOf(num));
        double value = two.setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue();
        return Double.parseDouble(String.format("%.2f", value).replace(",", "."));
    }

    //获取带三位小数的double数
    @SuppressLint("DefaultLocale")
    public static double getDotThreeDouble(double num) {
        BigDecimal decimal = new BigDecimal(String.valueOf(num));
        double value = decimal.setScale(3, BigDecimal.ROUND_HALF_UP).doubleValue();
        return Double.parseDouble(String.format("%.3f", value).replace(",", "."));
    }

    //获取带一位小数的double数
    @SuppressLint("DefaultLocale")
    public static double getDotOneDouble(double num) {
        BigDecimal decimal = new BigDecimal(String.valueOf(num));
        double value = decimal.setScale(1, BigDecimal.ROUND_HALF_UP).doubleValue();
        return Double.parseDouble(String.format("%.1f", value).replace(",", "."));
    }

    //获取double的后三位数并转换为字符串，小数点最后一位是0就忽略
    public static String getDotThreeString(double num) {

        try {
            num = getDotThreeDouble(num);
            return doubleConvertToString(num);
            //return String.format("%.3f",num);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "0.000";
    }

    //获取double的后两位数并转换为字符串，小数点最后一位是0就忽略
    public static String getDotTwoString(double num) {
        try {
            num = getDotTwoDouble(num);
            return doubleConvertToString(num);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "0.00";
        //return String.format("%.2f",num);
    }

    //获取double的后一位数并转换为字符串，小数点最后一位是0就忽略
    public static String getDotOneString(double num) {
        try {
            num = getDotOneDouble(num);
            return doubleConvertToString(num);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "0.0";
        //return String.format("%.1f",num);
    }

    //获取有一位小数double的小数位值
    public static int getDotOneValueFromDouble(double value) {
        return (int) subtract(multiply(value, 10.0), multiply((int) value, 10.0));
        //return (int)(value * 10- (int)value*10);
    }

    //获取有三位小数double的小数位值
    public static int getDotThreeValueFromDouble(double value) {
        return (int) subtract(multiply(value, 1000.0), multiply((int) value, 1000.0));
        //return (int)(value * 100- (int)value*100);
    }

    //获取有两位小数double的小数位值
    public static int getDotTwoValueFromDouble(double value) {
        return (int) subtract(multiply(value, 100.0), multiply((int) value, 100.0));
        //return (int)(value * 1000- (int)value*1000);
    }

    //将string转为double
    public static double convertToDouble(String number, double defaultValue) {
        if (number == null || number.isEmpty())
            return defaultValue;
        try {
            return Double.parseDouble(number.replace(",", "."));
        } catch (Exception e) {
            Log.e("AaruyNumberUtils", e.toString());
            return defaultValue;
        }
    }

    //忽略小数点后最后一位的0
    public static String doubleConvertToString(double value) {
        double doubleValue = getDotThreeDouble(value);
        return doubleValue + "";
    }

    //忽略小数点后最后一位的0
    public static String convertDoubleToString(double value){
        BigDecimal bd = new BigDecimal(String.valueOf(value));
        return bd.stripTrailingZeros().toPlainString();
    }

    public static float dp2px(float dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, Resources.getSystem().getDisplayMetrics());
    }

}

