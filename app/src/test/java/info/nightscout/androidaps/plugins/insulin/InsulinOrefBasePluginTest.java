package info.nightscout.androidaps.plugins.insulin;

import org.junit.Before;
import org.junit.Test;

import info.nightscout.androidaps.data.Iob;
import info.nightscout.androidaps.plugins.treatments.Treatment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Created by triplem on 07.01.18.
 */

public class InsulinOrefBasePluginTest extends InsulinOrefBasePlugin {

    private int peak;
    private double dia;

    private boolean shortDiaNotificationSend;

    @Before
    public void setUp() throws Exception {
        this.peak = 0;
        this.dia = InsulinOrefBasePlugin.MIN_DIA;
        this.shortDiaNotificationSend = false;
    }

    @Test
    public void testGetDia() throws Exception {
        assertEquals(InsulinOrefBasePlugin.MIN_DIA, this.getDia(), 0);

        this.dia = InsulinOrefBasePlugin.MIN_DIA + 1;
        assertEquals(InsulinOrefBasePlugin.MIN_DIA + 1, this.getDia(), 0);

        this.dia = InsulinOrefBasePlugin.MIN_DIA - 1;
        assertEquals(InsulinOrefBasePlugin.MIN_DIA, this.getDia(), 0);
        assertTrue(this.shortDiaNotificationSend);
    }

    @Test
    public void testIobCalcForTreatment() {
        Treatment treatment = new Treatment();
        Iob expected = new Iob();

        assertEquals(expected, this.iobCalcForTreatment(treatment, 0, 0d));

        this.peak = 30;
        this.dia = 4;
        long time = System.currentTimeMillis();

        // check directly after bolus
        treatment.date = time;
        treatment.insulin = 10d;
        assertEquals(10d, this.iobCalcForTreatment(treatment, time).iobContrib, 0.1);

        // check after 1 hour
        treatment.date = time - 1 * 60 * 60 * 1000; // 1 hour
        treatment.insulin = 10d;
        assertEquals(3.92, this.iobCalcForTreatment(treatment, time).iobContrib, 0.1);

        // check after 2 hour
        treatment.date = time - 2 * 60 * 60 * 1000; // 1 hour
        treatment.insulin = 10d;
        assertEquals(0.77, this.iobCalcForTreatment(treatment, time).iobContrib, 0.1);

        // check after 3 hour
        treatment.date = time - 3 * 60 * 60 * 1000; // 1 hour
        treatment.insulin = 10d;
        assertEquals(0.10, this.iobCalcForTreatment(treatment, time).iobContrib, 0.1);

        // check after dia
        treatment.date = time - 4 * 60 * 60 * 1000;
        treatment.insulin = 10d;
        assertEquals(0d, this.iobCalcForTreatment(treatment, time).iobContrib, 0.1);
    }


    /**
     * this method is implemented to allow tests of the iobCalcForTreatment calculation
      * @return
     */
    @Override
    int getPeak() {
        return this.peak;
    }

     /**
     * Userdefined Dia is implemented to allow tests of the getDia method
     *
     * @return
     */
    public double getUserDefinedDia() {
        return this.dia;
    }

    void sendShortDiaNotification(double dia) {
        this.shortDiaNotificationSend = true;
    }

    @Override
    public int getId() {
        return 0;
    }

    @Override
    public String getFriendlyName() {
        return null;
    }

    @Override
    String commentStandardText() {
        return null;
    }


    // the following methods are implemented, but not needed...
}
