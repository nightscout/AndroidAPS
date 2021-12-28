package info.nightscout.androidaps.plugins.pump.combo.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import info.nightscout.androidaps.combo.R;
import info.nightscout.shared.sharedPreferences.SP;

/**
 * Created by andy on 3/17/18.
 */
public class ComboErrorUtil {

    private SP sp;

    Map<String, List<ErrorState>> errorMap = new HashMap<>();

    private static final ComboErrorUtil comboDataUtil = new ComboErrorUtil();

    private ComboErrorUtil() {
    }

    public static ComboErrorUtil getInstance() {
        return comboDataUtil;
    }

    public void setSP(SP sp) {
        this.sp = sp;
    }

    public void addError(Exception exception) {
        String exceptionMsg = exception.getMessage();

        if (!errorMap.containsKey(exceptionMsg)) {
            List<ErrorState> list = new ArrayList<>();
            list.add(createErrorState(exception));

            errorMap.put(exceptionMsg, list);
        } else {
            errorMap.get(exceptionMsg).add(createErrorState(exception));
        }

        updateErrorCount();
    }


    private void updateErrorCount() {
        int errorCount = 0;

        if (!isErrorPresent()) {
            for (List<ErrorState> errorStates : errorMap.values()) {
                errorCount += errorStates.size();
            }
        }

        if (errorCount==0) {
            if (sp.contains(R.string.key_combo_error_count)) {
                sp.remove(R.string.key_combo_error_count);
            }
        } else {
            sp.putInt(R.string.key_combo_error_count, errorCount);
        }
    }

    private ErrorState createErrorState(Exception exception) {
        ErrorState errorState = new ErrorState();
        errorState.setException(exception);
        errorState.setTimeInMillis(System.currentTimeMillis());

        return errorState;
    }

    public void clearErrors() {
        if (errorMap != null)
            this.errorMap.clear();
        else
            this.errorMap = new HashMap<>();

        if (sp.contains(R.string.key_combo_error_count)) {
            sp.remove(R.string.key_combo_error_count);
        }
    }

    public boolean isErrorPresent() {
        return !this.errorMap.isEmpty();
    }

    public int getErrorCount() {
        return sp.contains(R.string.key_combo_error_count) ?
                sp.getInt(R.string.key_combo_error_count, -1) : -1;
    }

    public DisplayType getDisplayType() {
        String displayTypeString = sp.getString(R.string.key_show_comm_error_count, "ON_ERROR");
        return DisplayType.valueOf(displayTypeString);
    }

    public enum DisplayType {
        NEVER,
        ON_ERROR,
        ALWAYS
    }

}
