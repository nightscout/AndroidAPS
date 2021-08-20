package info.nightscout.androidaps.utils;

import android.text.Html;
import android.text.Spanned;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;

import java.util.Iterator;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.text.DateFormat;

import info.nightscout.androidaps.logging.StacktraceLoggerWrapper;

/**
 * Created by mike on 11.07.2016.
 */
public class JSONFormatter {
    private static final Logger log = StacktraceLoggerWrapper.getLogger(JSONFormatter.class);

    public static Spanned format(final String jsonString) {
        final JsonVisitor visitor = new JsonVisitor(1, '\t');
        try {
            if (jsonString.equals("undefined"))
                return HtmlHelper.INSTANCE.fromHtml("undefined");
            else if (jsonString.getBytes()[0] == '[')
                return HtmlHelper.INSTANCE.fromHtml(visitor.visit(new JSONArray(jsonString), 0));
            else
                return HtmlHelper.INSTANCE.fromHtml(visitor.visit(new JSONObject(jsonString), 0));
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
            return HtmlHelper.INSTANCE.fromHtml("");
        }
    }

    public static Spanned format(final JSONObject object) {
        final JsonVisitor visitor = new JsonVisitor(1, '\t');
        try {
            return HtmlHelper.INSTANCE.fromHtml(visitor.visit(object, 0));
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
            return HtmlHelper.INSTANCE.fromHtml("");
        }
    }

    private static class JsonVisitor {
        private final int indentationSize;
        private final char indentationChar;

        public JsonVisitor(final int indentationSize, final char indentationChar) {
            this.indentationSize = indentationSize;
            this.indentationChar = indentationChar;
        }

        private String visit(final JSONArray array, final int indent) throws JSONException {
            String ret = "";
            final int length = array.length();
            if (length == 0) {
            } else {
                ret += write("[", indent);
                for (int i = 0; i < length; i++) {
                    ret += visit(array.get(i), indent);
                }
                ret += write("]", indent);
            }
            return ret;
        }

        private String visit(final JSONObject obj, final int indent) throws JSONException {
            String ret = "";
            final int length = obj.length();
            if (length == 0) {
            } else {
                final Iterator<String> keys = obj.keys();
                while (keys.hasNext()) {
                    final String key = keys.next();
                    ret += write("<b>" + key + "</b>: ", indent);
                    ret += visit(obj.get(key), indent + 1);
                    ret += "<br>";
                }
            }
            return ret;
        }

        private String visit(final Object object, final int indent) throws JSONException {
            String ret = "";
            Long n;
            if (object instanceof JSONArray) {
                ret += visit((JSONArray) object, indent);
            } else if (object instanceof JSONObject) {
                ret += "<br>" + visit((JSONObject) object, indent);
            } else {
                if (object instanceof String) {
                    ret += write("\"" + ((String) object).replace("<", "&lt;").replace(">", "&gt;") + "\"", indent);
                } else {
                    // try to detect Date as milliseconds
                    if (object instanceof Long) {
                        n = (Long) object;
                        if (n > 1580000000000L && n < 2000000000000L) { // from 2020.01.26 to 2033.05.18 it is with high probability a date object
                            Date date = new Date(n);
                            DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                            ret += write(formatter.format(date), indent);
                        } else {
                            ret += write(String.valueOf(object), indent);
                        }
                    } else {
                        ret += write(String.valueOf(object), indent);
                    }
                }
            }
            return ret;
        }

        private String write(final String data, final int indent) {
            String ret = "";
            for (int i = 0; i < (indent * indentationSize); i++) {
                ret += indentationChar;
            }
            ret += data;
            return ret;
        }
    }
}
