package info.nightscout.androidaps.utils;

import android.os.Build;
import android.text.Html;
import android.text.Spanned;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;

/**
 * Created by mike on 11.07.2016.
 */
public class JSONFormatter {
    private static Logger log = LoggerFactory.getLogger(JSONFormatter.class);

    public static Spanned format(final String jsonString) {
        final JsonVisitor visitor = new JsonVisitor(1, '\t');
        try {
            if (jsonString.equals("undefined"))
                return Html.fromHtml("undefined");
            else if (jsonString.getBytes()[0] == '[')
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    return Html.fromHtml(visitor.visit(new JSONArray(jsonString), 0), Html.FROM_HTML_MODE_COMPACT);
                } else {
                    return Html.fromHtml(visitor.visit(new JSONArray(jsonString), 0));
                }
            else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    return Html.fromHtml(visitor.visit(new JSONObject(jsonString), 0), Html.FROM_HTML_MODE_COMPACT);
                } else {
                    return Html.fromHtml(visitor.visit(new JSONObject(jsonString), 0));
                }
            }
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
            return Html.fromHtml("");
        }
    }

    public static Spanned format(final JSONObject object) {
        final JsonVisitor visitor = new JsonVisitor(1, '\t');
        try {
            return Html.fromHtml(visitor.visit(object, 0));
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
            return Html.fromHtml("");
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
            if (object instanceof JSONArray) {
                ret += visit((JSONArray) object, indent);
            } else if (object instanceof JSONObject) {
                ret += "<br>" + visit((JSONObject) object, indent);
            } else {
                if (object instanceof String) {
                    ret += write("\"" + ((String) object).replace("<", "&lt;").replace(">", "&gt;") + "\"", indent);
                } else {
                    ret += write(String.valueOf(object), indent);
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