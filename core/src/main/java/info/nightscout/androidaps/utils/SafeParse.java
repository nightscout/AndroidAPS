package info.nightscout.androidaps.utils;

/**
 * Created by mike on 23.06.2016.
 */
public class SafeParse {
    // TODO return logging with dagger
//    private static Logger log = StacktraceLoggerWrapper.getLogger(SafeParse.class);
    public static Double stringToDouble(String input) {
        Double result = 0d;
        input = input.replace(",", ".");
        input = input.replace("−", "-");
        if (input.equals(""))
            return 0d;
        try {
            result = Double.parseDouble(input);
        } catch (Exception e) {
//            log.error("Error parsing " + input + " to double");
        }
        return result;
    }

    public static Integer stringToInt(String input) {
        Integer result = 0;
        input = input.replace(",", ".");
        input = input.replace("−", "-");
        if (input.equals(""))
            return 0;
        try {
            result = Integer.parseInt(input);
        } catch (Exception e) {
//            log.error("Error parsing " + input + " to int");
        }
        return result;
    }

    public static Long stringToLong(String input) {
        Long result = 0L;
        input = input.replace(",", ".");
        input = input.replace("−", "-");
        if (input.equals(""))
            return 0L;
        try {
            result = Long.parseLong(input);
        } catch (Exception e) {
//            log.error("Error parsing " + input + " to long");
        }
        return result;
    }
}
