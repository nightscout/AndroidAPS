package info.nightscout.utils;

import android.content.Context;
import android.view.LayoutInflater;

import org.json.JSONArray;
import org.junit.Test;
import org.mockito.Mock;

import java.lang.reflect.Method;
import java.text.DecimalFormat;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

/**
 * Created by mike on 30.12.2016.
 */
public class TimeListEditTest {

    /*
        JSONArray data = new JSONArray();
        JSONArray data2 = new JSONArray();
        TimeListEdit tle = new TimeListEdit(null, null, 0, "Test1", data, "ic", null, new DecimalFormat("0.00"));
        TimeListEdit tle2 = new TimeListEdit(null, null, 0, "Test2", data2, "ic", "ic2", new DecimalFormat("0.00"));


        @Test
        public void doArrayTest() throws Exception {
            tle.addItem(0, 0, 0.1, 0);
            tle.addItem(1, 60 * 60, 0.2, 0);
            assertEquals(2, tle.itemsCount());

            tle.editItem(0, 2 * 60 * 60, 1, 0);
            assertEquals(2, tle.itemsCount());
            assertEquals(1d, tle.value1(0), 0.00001d);
            assertEquals( 2 * 60 * 60, tle.secondFromMidnight(0));
            tle.removeItem(0);
            assertEquals(0.2d, tle.value1(0), 0.00001d);
            assertEquals(0, tle.value2(0), 0.00001d);
            assertEquals(60 * 60, tle.secondFromMidnight(0));

            //System.out.print(tle2.toString());
            assertEquals(0, tle2.itemsCount());
            tle2.addItem(0, 0, 1, 2);
            assertEquals(1, tle2.itemsCount());
            assertEquals(0, tle2.secondFromMidnight(0));
            assertEquals(1d, tle2.value1(0), 0.00001d);
            assertEquals(2d, tle2.value2(0), 0.00001d);
        }

    */
    @Test
    public void fakeTest() throws Exception {
    }

}