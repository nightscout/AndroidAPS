package info.nightscout.androidaps.interaction.utils;

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

   public static Long stringToLong(String input) {
       Long result = 0L;
       input = input.replace(",", ".");
       try {
           result = Long.parseLong(input);
       } catch (Exception e) {
       }
       return result;
   }
   
   public static Float stringToFloat(String input) {
       Float result = 0f;
       input = input.replace(",", ".");
       try {
           result = Float.valueOf(input);
       } catch (Exception e) {
       }
       return result;
    }
}
