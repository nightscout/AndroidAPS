package com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpData;

import android.content.ContentValues;
import android.content.Context;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;

import com.gxwtech.roundtrip2.RT2Const;
import com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpData.records.Record;
import com.gxwtech.roundtrip2.util.StringUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.util.ArrayList;

/**
 * Created by geoff on 6/17/16.
 */
public class PumpHistoryManager {
    private static final String TAG = "PumpHistoryManager";
    private Context context;
    private PumpHistoryDatabaseHandler phdb;
    ArrayList<ContentValues> dbContentValues = new ArrayList<>();
    ArrayList<Bundle> packedPages = null;
    public PumpHistoryManager(Context context) {
        this.context = context;
        phdb = new PumpHistoryDatabaseHandler(context);
    }

    public void initFromPages(Bundle historyBundle) {
        packedPages = historyBundle.getParcelableArrayList(RT2Const.IPC.MSG_PUMP_history_key);
        for (int i=0; i<packedPages.size(); i++) {
            Bundle pageBundle = packedPages.get(i);
            if (pageBundle != null) {
                ArrayList<Bundle> recordBundleList = pageBundle.getParcelableArrayList("mRecordList");
                for (Bundle b : recordBundleList) {
                    try {
                        PumpHistoryDatabaseEntry entry = new PumpHistoryDatabaseEntry();
                        if (entry.initFromRecordBundle(i, b)) {
                            dbContentValues.add(entry.getContentValues());
                        }
                    } catch (java.lang.NullPointerException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        phdb.addContentValuesList(dbContentValues);
    }

    public void clearDatabase() {
        phdb.clearPumpHistoryDatabase();
    }

    /* Checks if external storage is available for read and write */
    public boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }

    public boolean timestampOK(String timestamp) {
        if (timestamp == null) {
            return false;
        }
        if (timestamp.length() < 4) {
            return false;
        }
        if ("2015".equals(timestamp.substring(0,4))) {
            return true;
        }
        if ("2016".equals(timestamp.substring(0,4))) {
            return true;
        }
        return false;
    }

    public HtmlCodeTagStart renderContexts(ArrayList<Bundle> relevantBundles) {
        // search for all contexts that span this byte
        // make a start tag that represents them.
        StringBuilder titleBuilder = new StringBuilder();
        for (Bundle b : relevantBundles) {
            String name = b.getString("_type");
            String timestamp = b.getString("timestamp");
            int length = b.getInt("length");
            int offset = b.getInt("foundAtOffset");
            titleBuilder.append(String.format("[%s%s %s l=%d o=%d]",
                    timestampOK(timestamp) ? "" : "BAD ",
                    name==null?"(null)":name,
                    timestamp == null?"(null)":timestamp,
                    length, offset));
        }
        String colorString = null;
        if (relevantBundles.size() == 1) {
            if (timestampOK(relevantBundles.get(0).getString("timestamp"))) {
                colorString = "#99ff99";
            } else {
                colorString = "#F0E68C";
            }
        } else if (relevantBundles.size() > 1) {
            boolean allOK = relevantBundlesAllOK(relevantBundles);
            boolean allBad = relevantBundlesAllBad(relevantBundles);
            if (allOK) {
                colorString = "#248f24";
            } else if (allBad) {
                colorString = "#cc0000";
            } else {
                colorString = "#b3b300";
            }
        }
        return new HtmlCodeTagStart(titleBuilder.toString(),colorString);
    }

    public ArrayList<Bundle> findRelevantBundles(int pageNum, int pageOffset) {
        ArrayList<Bundle> relevantBundles = new ArrayList<>();
        ArrayList<Bundle> recordBundleList = packedPages.get(pageNum).getParcelableArrayList("mRecordList");
        for (int i=0; i< recordBundleList.size(); i++) {
            Bundle recordBundle = recordBundleList.get(i);
            int offset = recordBundle.getInt("foundAtOffset");
            int length = recordBundle.getInt("length");
            if ((offset <= pageOffset) && (offset + length > pageOffset)) {
                relevantBundles.add(recordBundle);
            }
        }
        return relevantBundles;
    }

    public boolean relevantBundlesAllOK(ArrayList<Bundle> bundles) {
        for (Bundle b : bundles) {
            String ts = b.getString("timestamp");
            if (ts == null) {
                return false;
            }
            if (!timestampOK(ts)) {
                return false;
            }

        }
        return true;
    }

    public boolean relevantBundlesAllBad(ArrayList<Bundle> bundles) {
        for (Bundle b : bundles) {
            String ts = b.getString("timestamp");
            if (ts != null) {
                if (timestampOK(ts)) {
                    return false;
                }
            }
        }
        return true;
    }

    public boolean relevantBundlesSame(ArrayList<Bundle> bundles1, ArrayList<Bundle> bundles2) {
        if (bundles1.size() != bundles2.size()) {
            return false;
        }
        for (Bundle b : bundles1) {
            if (!bundles2.contains(b)) {
                return false;
            }
        }
        for (Bundle b : bundles2) {
            if (!bundles1.contains(b)) {
                return false;
            }
        }
        return true;
    }

    public boolean relevantBundlesAllDifferent(ArrayList<Bundle> bundles1, ArrayList<Bundle> bundles2) {
        for (Bundle b : bundles1) {
            if (bundles2.contains(b)) {
                return false;
            }
        }
        for (Bundle b : bundles2) {
            if (bundles1.contains(b)) {
                return false;
            }
        }
        return true;
    }

    public ArrayList<HtmlElement> makeDom() {
        ArrayList<HtmlElement> rval = new ArrayList<>();
        for (int pageNum = 0; pageNum < packedPages.size(); pageNum++) {
            Log.i(TAG,"Rendering page " + pageNum);
            rval.add(new HtmlHistoryPageStart(pageNum));
            byte[] pageData = packedPages.get(pageNum).getByteArray("data");
            if (pageData == null) {
                return rval;
            }
            int pageSize = pageData.length;
            byte[] crc = packedPages.get(pageNum).getByteArray("crc");
            if (pageSize != 1022) {
                Log.e(TAG, "Page size is not 1022, it is " + pageSize);
            }
            int pageOffset = 0;
            boolean done = false;
            ArrayList<Bundle> currentBundles = findRelevantBundles(pageNum, pageOffset);
            ArrayList<Bundle> nextBundles = new ArrayList<>();
            rval.add(renderContexts(currentBundles));
            while (!done) {
                rval.add(new HtmlByte(pageData[pageOffset]));
                if (pageOffset == pageSize - 1) {
                    done = true;
                } else {
                    nextBundles = findRelevantBundles(pageNum, pageOffset + 1);
                    if (relevantBundlesSame(currentBundles, nextBundles)) {
                        // Not changing context
                        if (pageOffset % 32 == 31) {
                            rval.add(new HtmlElementGeneric("<br>\n"));
                        } else {
                            rval.add(new HtmlElementGeneric(" "));
                        }
                    } else {
                        // Changing context
                        rval.add(new HtmlCodeTagEnd());
                        if (relevantBundlesAllDifferent(currentBundles, nextBundles)) {
                            rval.add(new HtmlCodeTagStart("", "")); // <code>
                            if (pageOffset % 32 == 31) {
                                rval.add(new HtmlElementGeneric("<br>\n"));
                            } else {
                                rval.add(new HtmlElementGeneric(" "));
                            }
                            if (nextBundles.size() != 0) {
                                rval.add(new HtmlCodeTagEnd());
                                rval.add(renderContexts(nextBundles));
                            }
                        } else {
                            if (pageOffset % 32 == 31) {
                                rval.add(new HtmlElementGeneric("<br>\n"));
                            } else {
                                rval.add(new HtmlElementGeneric(" "));
                            }
                            rval.add(new HtmlCodeTagEnd());
                            rval.add(renderContexts(nextBundles));
                        }
                    }
                }
                if (!done) {
                    pageOffset++;
                    currentBundles = nextBundles;
                }
            }
        }
        return rval;
    }

    public void writeHtmlPage() {

        /*
        final String key_timestamp = "timestamp";
        String timestampString;
        final String key_pageNum = "pagenum";
        int pageNum;
        final String key_pageOffset = "offset";
        int foundAtOffset;
        final String key_recordType = "type";
        String recordType;
        final String key_length = "length";
        int length;
*/
        if (!isExternalStorageWritable()) {
            Log.e(TAG,"External storage not writable.");
            return;
        }

        String filename = "PumpHistoryBytes.html";

        File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
        File pageFile = new File(path,filename);

        OutputStream os = null;
        try {
            os = new FileOutputStream(pageFile);
        } catch (FileNotFoundException fnf) {
            Log.e(TAG,"Failed to open " + filename + " for writing");
        }
        if (os == null) {
            return;
        }

        // write header
        try {
            os.write("<!DOCTYPE html>".getBytes());
            os.write("<html>".getBytes());
            os.write("<head>".getBytes());
            os.write("<title>Page Title</title>".getBytes());
            os.write("</head>".getBytes());
            os.write("<body>".getBytes());

            byte[] pageData = packedPages.get(0).getByteArray("data");
            int pageSize = pageData.length;
            //byte[] crc = packedPages.get(0).getByteArray("crc");
            if (pageSize != 1022) {
                Log.e(TAG,"Page size is not 1022, it is " + pageSize);
            }

            ArrayList<HtmlElement> dom = makeDom();
            Log.i(TAG,"There are " + dom.size() + " elements to render.");
            for (HtmlElement e : dom) {
                if (e != null) {
                    String elementString = e.toString();
                    if (elementString != null) {
                        byte[] bytes = elementString.getBytes();
                        if (bytes != null) {
                            os.write(bytes);
                        } else {
                            Log.e(TAG,"WriteHtmlPage: bytes is null");
                        }
                    } else {
                        Log.e(TAG,"WriteHtmlPage: elementString is null");
                    }
                } else {
                    Log.e(TAG,"WriteHtmlPage: element is null");
                }
            }

            os.write("</body></html>".getBytes());
            os.close();
        } catch (FileNotFoundException fnf) {
            fnf.printStackTrace();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }


}
