package info.nightscout.utils;

/**
 * Created by mike on 23.06.2016.
 */
public class SafeParse {
    public static Double stringToDouble(String input) {
        Double result = 0d;
        input = input.replace(",", ".");
        try {
            result = Double.parseDouble(input);
        } catch (Exception e) {
        }
        return result;
    }

    public static Integer stringToInt(String input) {
        Integer result = 0;
        input = input.replace(",", ".");
        try {
            result = Integer.parseInt(input);
        } catch (Exception e) {
        }
        return result;
    }
}
